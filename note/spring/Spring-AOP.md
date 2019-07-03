[TOC]

# Spring-AOP

## 	AOP是什么

​		与OOP对比，AOP是处理一些横切性问题，这些横切性问题不会影响到主逻辑实现的，但是会散落到代码的各个部分，难以维护。AOP就是把这些问题和主业务逻辑分开，达到与主业务逻辑解耦的目的。

​		AOP将主逻辑中横切性问题和主逻辑分开，达到和主逻辑解耦的目的，易于维护。

## 编程方式

​		切面必须是个bean交给spring容器管理

### 	 开启AOP

  - javaconfig

    ```java
    @Configuration
    @EnableAspectJAutoProxy
    public class AppConfig {
    
    }
    ```

- xml配置

  ```xml
  <aop:aspectj-autoproxy/>
  ```

### AOP概念

- aspect：一定要交给spring管理，是用来描述切面的抽象

- Joinpoint：连接点 目标对象（是我们要关注和增强的方法）

- ponitcut：切点表示连接点的集合（切点告诉我们连接点的位置，决定连接点的数量）

- Weaving：把代理逻辑织入到目标对象上的过程叫做织入

- target：目标对象

- aop Proxy：代理对象

- advice：通知 包括位置和逻辑两部分

  通知类型：

  - Before 连接点执行之前，但是无法阻止连接点的正常执行，除非该段执行抛出异常
  - After    连接点正常执行之后，执行过程中正常执行返回退出，非异常退出
  - After throwing 执行抛出异常的时候
  - After (finally)  无论连接点是正常退出还是异常退出，都会执行
  - Around advice  围绕连接点执行，例如方法调用。这是最有用的切面方式。around通知可以在方法调用之前和之后执行自定义行为。它还负责选择是继续加入点还是通过返回自己的返回值或抛出异常来快速建议的方法执行。

  - Proceedingjoinpoint 和JoinPoint的区别:

    Proceedingjoinpoint 继承了JoinPoint,proceed()这个是aop代理链执行的方法。并扩充实现了proceed()方法，用于继续执行连接点。JoinPoint仅能获取相关参数，无法执行连接点。

  - JoinPoint的方法
    - 1.java.lang.Object[] getArgs()：获取连接点方法运行时的入参列表； 
    - 2.Signature getSignature() ：获取连接点的方法签名对象； 
    - 3.java.lang.Object getTarget() ：获取连接点所在的目标对象； 
    - 4.java.lang.Object getThis() ：获取代理对象本身；
      proceed()有重载,有个带参数的方法,可以修改目标方法的的参数
      Introductions
  - perthis
    使用方式如下：
    @Aspect("perthis(this(com.chenss.dao.IndexDaoImpl))")
    要求：
    - AspectJ对象的注入类型为prototype
    - 目标对象也必须是prototype的
      原因为：只有目标对象是原型模式的，每次getBean得到的对象才是不一样的，由此针对每个对象就会产生新的切面对象，才能产生不同的切面结果

### 切点表达式

1. execution

   ```json
   execution(modifiers-pattern? ret-type-pattern declaring-type-pattern?name-pattern(param-pattern) throws-pattern?)
   这里问号表示当前项可以有也可以没有，其中各项的语义如下
   modifiers-pattern：方法的可见性，如public，protected；
   ret-type-pattern：方法的返回值类型，如int，void等；
   declaring-type-pattern：方法所在类的全路径名，如com.spring.Aspect；
   name-pattern：方法名类型，如buisinessService()；
   param-pattern：方法的参数类型，如java.lang.String；
   throws-pattern：方法抛出的异常类型，如java.lang.Exception；
   ```

2. within 最小粒度为类

   ```
   @Pointcut("within(com.chenss.dao.*)")//匹配com.chenss.dao包中的任意方法
   @Pointcut("within(com.chenss.dao..*)")//匹配com.chenss.dao包及其子包中的任意方法
   ```

3. args

   args表达式的作用是匹配指定参数类型和指定参数数量的方法,与包名和类名无关

   ```
   /**
   - args同execution不同的地方在于：
   - args匹配的是运行时传递给方法的参数类型
   - execution(* *(java.io.Serializable))匹配的是方法在声明时指定的方法参数类型。
     */
     @Pointcut("args(java.io.Serializable)")//匹配运行时传递的参数类型为指定类型的、且参数个数和顺序匹配
     @Pointcut("@args(com.chenss.anno.Chenss)")//接受一个参数，并且传递的参数的运行时类型具有@Classifi
   ```

4. this JDK代理时，指向接口和代理类proxy，cglib代理时 指向接口和子类(不使用proxy)

5. target  指向接口和子类

   ```
   /**
   
   - 此处需要注意的是，如果配置设置proxyTargetClass=false，或默认为false，则是用JDK代理，否则使用的是CGLIB代理
   - JDK代理的实现方式是基于接口实现，代理类继承Proxy，实现接口。
   - 而CGLIB继承被代理的类来实现。
   - 所以使用target会保证目标不变，关联对象不会受到这个设置的影响。
   - 但是使用this对象时，会根据该选项的设置，判断是否能找到对象。
     */
     @Pointcut("target(com.chenss.dao.IndexDaoImpl)")//目标对象，也就是被代理的对象。限制目标对象为com.chenss.dao.IndexDaoImpl类
     @Pointcut("this(com.chenss.dao.IndexDaoImpl)")//当前对象，也就是代理对象，代理对象时通过代理目标对象的方式获取新的对象，与原值并非一个
     @Pointcut("@target(com.chenss.anno.Chenss)")//具有@Chenss的目标对象中的任意方法
     @Pointcut("@within(com.chenss.anno.Chenss)")//等同于@targ
   ```

6. @annotation 

   ```
   作用方法级别
   上述所有表达式都有@ 比如@Target(里面是一个注解类xx,表示所有加了xx注解的类,和包名无关)
   注意:上述所有的表达式可以混合使用,|| && !
   @Pointcut("@annotation(com.chenss.anno.Chenss)")//匹配带有com.chenss.anno.Chenss注解
   ```

7. bean

   ```
   @Pointcut("bean(dao1)")//名称为dao1的bean上的任意方法
   @Pointcut("bean(dao*)")
   ```

   