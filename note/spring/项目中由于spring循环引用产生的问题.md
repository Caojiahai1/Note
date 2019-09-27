## 项目中遇到Spring循环依赖报错造成项目启动慢问题

### 问题描述

最近项目突然启动很慢，大概有10多分钟才能启动起来，平时一般一两分钟就能启动起来，项目没有报错只是启动特别慢，所以一定是最近提交的代码有问题。但是查看最近提交的代码，也看不出有什么问题，所以这个问题就有点诡异。那么只能断点进入源码找具体慢的地方。

通过排查发现在DruidDataSource中加了一个自定义Filter，然后这个Filter中注入了一个Dao导致项目启动很慢。为什么会这样呢？

在源码断点调试的过程中，发现spring在创建dao层bean的时候会在注入sqlSession的时候抛出BeanCurrentlyInCreationException异常，大致可以确定在注入sqlSession时循环依赖导致的。

### dao动态代理注入流程

mybatis的dao层是通过动态代理的方式注入的到spring容器中的，这个动态代理的过程是交给一个叫MapperFactoryBean的FactoryBean来执行的，FactoryBean通过执行getObject方法创建一个Bean，底层通过MapperProxy.newMapperProxy(type, sqlSession);这一行代码创建出一个代理对象，通过看MapperProxy源码会发现，这个代理对象需要注入一个sqlSession

下面是项目中sqlSession相关的配置

```xml
<bean id="sqlSessionFactoryBalance" class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="db_balance"/>
        <property name="typeAliasesPackage" value="com.qs.erp.entitys.entity,com.qs.erp.entitys.businessmodel"/>
        <property name="mapperLocations" value="classpath*:/sqlmap*/*Mapper.xml"/>
</bean>
<bean id="sqlSessionBalance" class="org.mybatis.spring.SqlSessionTemplate">
    <constructor-arg index="0" ref="sqlSessionFactoryBalance" />
</bean>
<bean id="db_balance" class="com.alibaba.druid.pool.DruidDataSource" init-method="init" destroy-method="close">
	<!-- datasource只贴出问题的地方 -->
    <property name="proxyFilters">
        <list>
            <ref bean="stat_filter"/>
            <ref bean="wall_filter"/>
        </list>
    </property>

</bean>
```

在开始创建bean的时候会先把beanName放入singletonsCurrentlyInCreation这个缓存map中，表示当前正在创建中的bean，在创建完代理对象，spring会把这个对象放入earlySingletonObjects缓存map中用于循环依赖的时候从缓存中取引用的对象，。然后进行sqlSession注入，第一次注入的时候，sqlSession还没有注入到beanFactory中，所以需要先创建一个sqlSession Bean，项目中用的是SqlSessionTemplate，这个类实例化的时候需要引用一个构造方法参数SqlSessionFactoryBean，因为需要传入一个参数，此时sqlSession还没有被实例化出来（也就是earlySingletonObjects缓存中没有sqlSession对象，但是singletonsCurrentlyInCreation这个map中已经加入了sqlSession，这边是坑的关键点），接下来就需要先创建SqlSessionFactoryBean这个bean同上面的步骤，SqlSessionFactoryBean是一个FactoryBean，所以会调用getObject返回一个SqlSessionFactory对象，这一步会对SqlSessionFactory进行初始化，其中就包括dataSource的初始化，在初始化到stat_filter这个Filter的时候，其中注入了一个SellOrderExtDao。

此时SellOrderExtDao还没有注入到beanFactory中，所以又会重复上面的步骤，当注入sqlSession会先从earlySingletonObjects这个缓存map中取sqlSession对象，但是此时取不出来。那么就会又去实例化sqlSession，在实例化之前spring会先判断当前bean是否正在创建中，发现sqlSession这个bean正在创建中，由于是单例的bean所以会直接抛出BeanCurrentlyInCreationException异常。

第一个dao创建bean失败后会继续往下循环其它的dao，同理其它的dao也都会抛出异常，但一直在循环执行上面的步骤。照理说dao不能注入到beanFactory中，项目启动应该会失败。但是当dao循环到SellOrderExtDao的时候，同上面步骤会执行到stat_filter这个Filter，此时在stat_filter中注入SellOrderExtDao时直接可以从earlySingletonObjects缓存中取出SellOrderExtDao代理对象，不会继续往下循环依赖，所以此时SellOrderExtDao成功注入到beanFactory中，sqlSession、SqlSessionFactoryBean、SqlSessionFactory、dataSource也都成功注入，之后再依赖到这些bean的时候可以直接从beanFactory中取出。

所以启动慢是由于在循环到SellOrderExtDao之前，一直在重复的进行失败的注入，并且每次注入都会往下循环依赖直到抛出异常。对于那些前面注入失败的dao，spring后面会重新注入进来。

### 解决方案

找到具体原因后解决就很简单了，可以把filter中注入的dao设置为懒加载，或者在方法中使用到这个dao的时候在从spring容器中取出来，反正只要不要让这个dao在启动的时候就注入进来就可以解决了。