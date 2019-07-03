[TOC]

## IOC（控制反转）

是面向对象编程中的一种设计原则，可以用来减低计算机代码之间的耦合度。DI（依赖注入）是IOC的一种实现

## DI（依赖注入）

A.Class中有B.Class属性，则A依赖了B。

IOC容器将B给A的过程称为注入。

###  注入两种方式

set方法注入，构造方法注入

## spring编程风格

### xml配置

```xml
<bean id="dao" class="test.dao.TestDAOImpl"></bean>
<bean id="testService" class="test.service.TestService">     		<property name="testDAO" ref="dao"></property>
</bean>
```

### annotation注解方式

在xml文件使用一下配置开启扫描注解模式

```xml
<context:component-scan base-package="test"></context:component-scan>
```

javaconfig使用@ComponentScan("test")注解开启扫描

### javaconfig

@Configuration该注解表示该类为javaconfig类

使用如下代码启动spring获取全局环境context

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringConfig.class);
```

## 自动装配

### by type

使用bean属性的类型来匹配，如果有多个bean符合则会报错。

### by name

根据属性的名字找到相同beanName的Bean注入进来。

### 注解

- @Autowired注解：

  使用此注解默认是根据by type来匹配，如果有多个Bean的类型符合当前需要装配的属性，则会再根据beanName来匹配，如果有则取同名类的bean注入进来，否则报错。

  @Qualifier注解，限定符指定具体用哪个beanName

  @Primary注解，如果在bean上使用此注解，则当注入属性时找到多个bean时，取使用此注解的bean注入

  @Priority注解，此注解用来表示bean被注入的优先级，数字越小优先级越高

- @Resource注解：按照by name来匹配，默认使用属性名，也可以传入具体的beanName 

## Bean作用域

singleton、prototype

一个singleton Bean中注入一个prototype属性的bean，每次获取bean的时候其对应的属性也是同一个bean，因为single的bean只会注入一次。

## 自定义beanName规则

可以实现BeanNameGenerator接口，写一个自定义MyBeanNameGenerator

javaconfig上加上下面注解

```java
@ComponentScan(value = "test", nameGenerator = MyBeanNameGenerator.class)
```

xml配置方式

```xml
<context:component-scan base-package="test" name-generator="test.common.MyBeanNameGenerator"></context:component-scan>
```