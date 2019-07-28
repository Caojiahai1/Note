# Spring日志体系

在研究spring日志之前，首先了解一下java几种日志的使用。

## Log4j

log4j是Apache的开源项目，在maven中引入下面依赖，添加配置文件就可以使用

依赖

```xml
<dependency>
    <groupId>log4j</groupId>
    <artifactId>log4j</artifactId>
    <version>1.2.17</version>
</dependency>
```

log4j.properties配置

```properties
log4j.rootLogger=info,systemOut

log4j.appender.systemOut= org.apache.log4j.ConsoleAppender
log4j.appender.systemOut.layout= org.apache.log4j.PatternLayout
log4j.appender.systemOut.layout.ConversionPattern= [%-5p][%-22d{yyyy/MM/dd HH:mm:ssS}][%l]%n%m%n
log4j.appender.systemOut.Threshold= DEBUG
log4j.appender.systemOut.ImmediateFlush= TRUE
log4j.appender.systemOut.Target= System.out
```

使用示例

```java
package com.javalog;

import org.apache.log4j.Logger;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class Log4JTest {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(Log4JTest.class);
        logger.info("log4j");
    }
}
```

打印结果

```
[INFO ][2019/07/28 10:56:35999][com.javalog.Log4JTest.main(Log4JTest.java:14)]
log4j
```

## JUL

jul是java util包下自带的日志api，可以直接使用

代码示例

```java
package com.javalog;

import java.util.logging.Logger;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class JULTest {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger("jul");
        logger.info("jul");
    }
}
```

打印结果

```
七月 28, 2019 10:59:51 上午 com.javalog.JULTest main
信息: jul
```

## JCL（commons-logging）

jcl是Apache提供的一个日志接口，避免项目中日志方案的耦合。

引入jcl依赖包

```xml
<dependency>
    <groupId>commons-logging</groupId>
    <artifactId>commons-logging-api</artifactId>
    <version>1.1</version>
</dependency>
<dependency>
    <groupId>log4j</groupId>
    <artifactId>log4j</artifactId>
    <version>1.2.17</version>
</dependency>
```

示例代码

```java
package com.javalog;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class JCLTest {

    public static void main(String[] args) {
        Log log = LogFactory.getLog("jcl");
        log.info("jcl");
    }
}
```

打印结果：此时打印出来的结果发现和log4j一样，实际上此时jcl使用的就是log4j

```
[INFO ][2019/07/28 11:40:06539][com.javalog.JCLTest.main(JCLTest.java:15)]
jcl
```

接下来将log4j依赖包去掉再打印：发现此时打印出来的jul的日志，接下来分析一下源码jcl具体怎么实现选择不同的日志框架进行打印的

```
七月 28, 2019 11:44:32 上午 jcl main
信息: jcl
```

源码分析：通过断点进入源码，发现这段代码来获取具体使用的日志框架

```java
for(int i = 0; i < classesToDiscover.length && result == null; ++i) {
    // classesToDiscover是一个保存了4个日志框架类入口全路径的数据
    // 0 = "org.apache.commons.logging.impl.Log4JLogger"
	// 1 = "org.apache.commons.logging.impl.Jdk14Logger"
	// 2 = "org.apache.commons.logging.impl.Jdk13LumberjackLogger"
	// 3 = "org.apache.commons.logging.impl.SimpleLog"
    // 从这个for循环可以看出，首先会判断Log4JLogger，如果存在则直接返回使用log4j，不存在依次往下找
    result = this.createLogFromClass(classesToDiscover[i], logCategory, true);
}
```

流程图

```flow
st=>start: jcl
e=>end: log
e2=>end: log
cond1=>condition: 是否引入了log4j
cond2=>condition: 是否引入了log4j
op=>operation: 使用jul
st->cond1
cond1(yes)->e
cond1(no)->op->e2->
&```
```



## Slf4j

Simple Logging Facade for Java 是SLF4J的英文全称，简单的java门面日志框架。

使用Slf4j需要引入Slf4j的核心包和绑定具体日志框架的包

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.7.25</version>
</dependency>
<!--此处不需要再引入log4j包，这个包中已经引入了log4j-->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-log4j12</artifactId>
    <version>1.7.25</version>
</dependency>
```

代码示例

```java
package com.javalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class Slf4jTest {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger("slf4j");
        logger.info("slf4j");
    }
}
```

打印结果

```
SLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/C:/Users/18652/.m2/repository/org/slf4j/slf4j-log4j12/1.7.25/slf4j-log4j12-1.7.25.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [jar:file:/C:/Users/18652/.m2/repository/ch/qos/logback/logback-classic/1.1.1/logback-classic-1.1.1.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: See http://www.slf4j.org/codes.html#multiple_bindings for an explanation.
SLF4J: Actual binding is of type [org.slf4j.impl.Log4jLoggerFactory]
// 上面是SLF4J打印的绑定Log4jLoggerFactory的日志，下面是我们代码打印的日志，可以看出使用的就是log4j
[INFO ][2019/07/28 12:11:07930][com.javalog.Slf4jTest.main(Slf4jTest.java:14)]
slf4j
```

使用Slf4j，可以有效的实现日志系统的解耦，如果项目中需要更换日志，只需要切换不同的绑定包即可，不需要修改任何代码。但是如果我们项目中引入了一个第三方项目，第三方项目使用的是jul，那么我们如何使整个项目的日志保持一致呢？

先看下面示例，Slf4j使用的是jdk绑定器，jcl使用的是log4j，分别使用Slf4j和jcl打印日志

引入依赖

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>4.0.8.RELEASE</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.7.25</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-jdk14</artifactId>
    <version>1.7.25</version>
</dependency>
<dependency>
    <groupId>log4j</groupId>
    <artifactId>log4j</artifactId>
    <version>1.2.17</version>
</dependency>
```

代码示例

```java
package com.javalog;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class Slf4jTest {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger("slf4j");
        logger.info("slf4j");
        Log log = LogFactory.getLog("jcl");
        log.info("jcl");
    }
}
```

打印结果

```
七月 28, 2019 12:48:23 下午 com.javalog.Slf4jTest main
信息: slf4j
[INFO ][2019/07/28 12:48:2411 ][com.javalog.Slf4jTest.main(Slf4jTest.java:18)]
jcl
```

从打印结果可以发现，我们使用slf4j打印的是jul日志，jcl打印的是log4j日志，造成了项目中日志结构混乱。当然这边我们只需要包log4j包去掉就可以保持一直，但如果第三方引入的包内部使用的是log4j，我们没法去修改。所以slf4j提供了解决方案，使用slf4j桥接器，将第三方包中log4j日志桥接到jul即可。

添加依赖，将jcl桥接到slf4j

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>jcl-over-slf4j</artifactId>
    <version>1.7.25</version>
</dependency>
```

打印结果

```
七月 28, 2019 12:51:39 下午 com.javalog.Slf4jTest main
信息: slf4j
七月 28, 2019 12:51:39 下午 com.javalog.Slf4jTest main
信息: jcl
```

此时打印的都是slf4j绑定的jul日志。

## spring5日志新特性

spring4和spring5都是使用jcl作为日志框架的，jcl具体使用看上面。

那么spring5日志和spring4日志到底有什么不同呢？接下来分别使用spring4和spring5打印log4j日志，从上面jcl原理我们知道只需要引入log4j依赖就可以了。

代码示例：

```java
package com.javalog;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author Yan liang
 * @create 2019/7/28
 * @since 1.0.0
 */
public class SpringTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Appconfig.class);
    }
}
```

spring4打印结果：

```
[INFO ][2019/07/28 13:58:12152][org.springframework.context.support.AbstractApplicationContext.prepareRefresh(AbstractApplicationContext.java:515)]
Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@442d9b6e: startup date [Sun Jul 28 13:58:12 CST 2019]; root of context hierarchy
```

spring5打印结果：

```
七月 28, 2019 2:03:55 下午 org.springframework.context.support.AbstractApplicationContext prepareRefresh
信息: Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@50134894: startup date [Sun Jul 28 14:03:55 CST 2019]; root of context hierarchy
```

从打印结果可以看出，spring4打印的是log4j的日志，但是spring5打印的是jul日志，这是为什么呢？不是都用的jcl吗，实际上spring5修改了jcl的源码，接下来分析一下spring5jcl源码，看看其实现原理。

```java
Log log = LogFactory.getLog(SpringTest.class);//进入这个方法

// 这个方法对logApi属性做了switch判断，所以看一下logApi什么时候赋值的，就可以知道这边会使用哪种日志
public static Log getLog(String name) {
    switch (logApi) {
        case LOG4J:
            // 这边的log4j是log4j2
            return Log4jDelegate.createLog(name);
        case SLF4J_LAL:
            return Slf4jDelegate.createLocationAwareLog(name);
        case SLF4J:
            return Slf4jDelegate.createLog(name);
        default:
            // Defensively use lazy-initializing delegate class here as well since the
            // java.logging module is not present by default on JDK 9. We are requiring
            // its presence if neither Log4j nor SLF4J is available; however, in the
            // case of Log4j or SLF4J, we are trying to prevent early initialization
            // of the JavaUtilLog adapter - e.g. by a JVM in debug mode - when eagerly
            // trying to parse the bytecode for all the cases of this switch clause.
            // 默认使用jul日志
            return JavaUtilDelegate.createLog(name);
    }
}

// logApi是在LogFactory初始化的时候就加载进来了，从下面逻辑可以看出
// 首先会判断Log4j2是否存在，如果存在则使用log4j2，不存在则往下依次找，找不到就会使用默认的jul日志
static {
		ClassLoader cl = LogFactory.class.getClassLoader();
		try {
			// Try Log4j 2.x API
            // 这边说明了是Log4j 2
			cl.loadClass("org.apache.logging.log4j.spi.ExtendedLogger");
			logApi = LogApi.LOG4J;
		}
		catch (ClassNotFoundException ex1) {
			try {
				// Try SLF4J 1.7 SPI
				cl.loadClass("org.slf4j.spi.LocationAwareLogger");
				logApi = LogApi.SLF4J_LAL;
			}
			catch (ClassNotFoundException ex2) {
				try {
					// Try SLF4J 1.7 API
					cl.loadClass("org.slf4j.Logger");
					logApi = LogApi.SLF4J;
				}
				catch (ClassNotFoundException ex3) {
					// Keep java.util.logging as default
				}
			}
		}
	}
```

从spring5jcl源码中我们可以看出，spring5使用日志优先级顺序为log4j2、SLF4J_LAL、SLF4J、jul。

所以如果在spring5中想使用log4j的话，需要使用SLF4J然后绑定log4j。