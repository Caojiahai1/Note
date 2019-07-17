# Java对象布局

## 分析工具

添加JOL依赖,利用JOL工具打印出java对象信息

```xml
<dependency>
    <groupId>org.openjdk.jol</groupId>
    <artifactId>jol-core</artifactId>
    <version>0.9</version>
</dependency>
```



## 布局分析

### 代码

定义一个A类

```java
public class A {

}
```

定义main方法，打印对象

```java
package test.sync;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.vm.VM;

/**
 * @author Yan liang
 * @create 2019/7/15
 * @since 1.0.0
 */
public class JOLExample {
    public static void main(String[] args) {
        A a = new A();
        System.out.println(VM.current().details());
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
    }
}
```

### 运行结果

<pre><font size = 1>
# Running 64-bit HotSpot VM.
# Using compressed oop with 0-bit shift.
# Using compressed klass with 3-bit shift.
# Objects are 8 bytes aligned.
# Field sizes by type: 4, 1, 1, 2, 2, 4, 4, 8, 8 [bytes]
# Array element sizes: 4, 1, 1, 2, 2, 4, 4, 8, 8 [bytes]</font></pre>
<pre><font size = 1>
sync.JOLExample$A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           01 00 00 00 (00000001 00000000 00000000 00000000) (1)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           c2 c0 00 20 (11000010 11000000 00000000 00100000) (536920258)
     12     4        (loss due to the next object alignment)
Instance size: 16 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total</font></pre>

### 分析

#### 不同类型对应大小

```
Field sizes by type: 4, 1, 1, 2, 2, 4, 4, 8, 8 [bytes]
```

对应：[Oop(Ordinary Object Pointer), boolean, byte, char, short, int, float, long, double]大小

#### 对象布局

这个对象一共16bytes大小,其中12bytes是对象头，4bytes是对齐字节。由于是64位机器，对象大小必须是8的倍数。由于这个对象里没有任何字段，故对象的实例数据为0bytes，需要使用4bytes对其字节来填充。

得出：一个对象布局分为对象头、对象实例数据、字节对齐三个部分。

从[openjdk官方文档](http://openjdk.java.net/groups/hotspot/docs/HotSpotGlossary.html)得知对象头分为**mark word**和**klass pointer**两部分

**object header**

```
Common structure at the beginning of every GC-managed heap object. (Every oop points to an object header.) Includes fundamental information about the heap object's layout, type, GC state, synchronization state, and identity hash code. Consists of two words. In arrays it is immediately followed by a length field. Note that both Java objects and VM-internal objects have a common object header format.

每个gc管理的堆对象开头的公共结构。(每个oop都指向一个对象头。)包括堆对象的布局、类型、GC状态、同步状态和标识哈希码的基本信息。由两个词组成。在数组中，它后面紧跟着一个长度字段。注意，Java对象和vm内部对象都有一个通用的对象头格式。
```

**mark word**

```
The first word of every object header. Usually a set of bitfields including synchronization state and identity hash code. May also be a pointer (with characteristic low bit encoding) to synchronization related information. During GC, may contain GC state bits.

每个对象头的第一个单词。通常是一组位域，包括同步状态和标识哈希码。也可以是指向同步相关信息的指针(具有特征的低比特编码)。在GC期间，可能包含GC状态位。
```

**klass pointer**

```
The second word of every object header. Points to another object (a metaobject) which describes the layout and behavior of the original object. For Java objects, the "klass" contains a C++ style "vtable".

每个对象头的第二个单词。指向描述原始对象的布局和行为的另一个对象(元对象)。对于Java对象，“klass”包含一个c++风格的“vtable”。
```

从上面打印信息可以看出，对象头部分大小为12b，其中8b为**mark word**，其余4b则属于**klass pointer**。下面主要分析**mark word**里面信息。



# mark word

## 无锁状态

**mark word**一共8bytes即64bits,在无锁状态下其分布情况如下表所示，前7b存的是hanshcode，后1b存的是gc年龄、是否偏向锁、对象状态。下面运行代码分析。

| unuse  | hashcode | unuse | gcage | biased_lock | lock  |
| ------ | -------- | ----- | ----- | ----------- | ----- |
| 25bits | 31bits   | 1bits | 4bits | 1bits       | 2bits |

### 代码

```java
package sync;

import org.openjdk.jol.info.ClassLayout;

/**
 * @author Yan liang
 * @create 2019/7/15
 * @since 1.0.0
 */
public class JOLExample1 {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        A a = new A();
        System.out.println("before hash");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
        System.out.println("JVM----------0x" + Integer.toHexString(a.hashCode()));
        HashUtil.countHash(a);
        System.out.println("after hash");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
    }
}
```

HashUtil工具类

```java
package sync;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * @author Yan liang
 * @create 2019/7/15
 * @since 1.0.0
 */
public class HashUtil {
    public static void countHash(Object object) throws NoSuchFieldException, IllegalAccessException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        long hashcode = 0;
        for (long index = 7; index > 0; index--) {
            // 取mark word中每一个byte进行计算
            hashcode |= (unsafe.getByte(object, index) & 0xFF) << ((index - 1) * 8);
        }
        String code = Long.toHexString(hashcode);
        System.out.println("Util---------0x" + code);
    }
}
```

### 运行结果

<pre><font size = 1>
before hash
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           01 00 00 00 (00000001 00000000 00000000 00000000) (1)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
JVM----------0x5e265ba4
Util---------0x5e265ba4
after hash
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           01 a4 5b 26 (00000001 10100100 01011011 00100110) (643539969)
      4     4        (object header)                           5e 00 00 00 (01011110 00000000 00000000 00000000) (94)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total</font></pre>

### 分析

由于是小端存储模式（数据的高字节保存在内存的高地址中，而数据的低字节保存在内存的低地址中），从运行结果可以看出，在hash之前，高位56个bits都是0，hash之后有了值，打印出16进制hashcode为0x5e265ba4，而且hash之后可以看出前25个bits还是0没有使用。

前56bits是hashcode，那剩下的00000001拆分成0 0000 0 01看，第一位0未被使用，0000表示gc年龄，0表示不是偏向锁，01则代表当前状态为无锁。

## 偏向锁

偏向锁前56bits中54bits存储的是线程ID,2bits存储的是epoch（偏向锁的时间戳），后8bits同无锁状态相同

偏向锁中没有hashcode，所以一单对象进行hash计算，就不能再成为偏向锁

| thread Id | epoch | unuse | gcage | biased_lock | lock  |
| --------- | ----- | ----- | ----- | ----------- | ----- |
| 54bits    | 2bits | 1bits | 4bits | 1bits       | 2bits |

### 代码

```java
package sync;

import org.openjdk.jol.info.ClassLayout;

/**
 * @author Yan liang
 * @create 2019/7/15
 * @since 1.0.0
 */
public class JOLExample2 {
    public static void main(String[] args) throws InterruptedException {
        //-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0   JVM参数禁用延迟
        Thread.sleep(4300L);
        A a = new A();
        System.out.println("before lock");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
        synchronized (a) {
            System.out.println("lock");
        }
        System.out.println("after lock");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
    }
}
```

### 运行结果

<pre><font size = 1>before lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           05 00 00 00 (00000101 00000000 00000000 00000000) (5)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
lock
after lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           05 38 95 02 (00000101 00111000 10010101 00000010) (43333637)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total</font></pre>

### 分析

代码中睡眠4300毫秒是因为JVM在启动的时候默认偏向锁延迟4s后开启，也可以修改JVM参数-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0将偏向锁延迟开启时间设为0。

JVM延迟开启偏向锁，是因为启动的时候JVM内部存在很多并发的线程在争抢一把锁，锁会从偏向锁往上膨胀，在这个过程中偏向锁确定基本没怎么使用，锁往上膨胀会消耗性能，所以做了延迟开启。

从都打印分析后8bits内容，拆分成0 0000 1 01看，0->unuse 0000->gc年龄 1->偏向锁 01->配合前一位为偏向锁

## 轻量锁

轻量锁前62bits存的是ptr_to_lock_record指向栈中锁记录的指针，后2bits存的是当前状态

| ptr_to_lock_record | lock  |
| ------------------ | ----- |
| 62bits             | 2bits |

### 代码

```java
package sync;

import org.openjdk.jol.info.ClassLayout;

/**
 * @author Yan liang
 * @create 2019/7/15
 * @since 1.0.0
 */
public class JOLExample2 {
    public static void main(String[] args) throws InterruptedException {
        //-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0   JVM参数禁用延迟
//        Thread.sleep(4300L);
        A a = new A();
        System.out.println("before lock");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
        synchronized (a) {
            System.out.println("lock");
            System.out.println(ClassLayout.parseInstance(a).toPrintable());
        }
        System.out.println("after lock");
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
    }
}
```

### 运行结果

<pre><font size = 1>before lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           01 00 00 00 (00000001 00000000 00000000 00000000) (1)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           a8 ee 9d 02 (10101000 11101110 10011101 00000010) (43904680)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
after lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           01 00 00 00 (00000001 00000000 00000000 00000000) (1)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total</font></pre>

### 分析

上面代码创建对象前没有进行睡眠，所以创建对象的时候还没启用偏向锁，进入同步块，直接变成轻量锁。这边只演示了锁状态在不可偏向状态下变成轻量锁，多个线程使用同一把锁交替运行，也会变成轻量锁。

在lock后打印出的即为轻量锁的状态，所以00代表的轻量锁的状态。

轻量锁在执行完同步代码块后会释放为无锁不可偏向状态，从运行结果after lock 后面打印的标志位为001可以看出。



## 重量锁

重量锁结构类似轻量锁，前62bits存储的是一个monitor对象，后2bits存放的是状态标识位

| ptr_to_heavyweight_monitor | lock  |
| -------------------------- | ----- |
| 62bits                     | 2bits |

### 代码

```java
package sync;

import org.openjdk.jol.info.ClassLayout;

/**
 * @author Yan liang
 * @create 2019/7/16
 * @since 1.0.0
 */
public class JOLExample7 {
    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(5000L);
        final A a = new A();
        // 偏向锁处于可偏向状态
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (a) {
                    try {
                        Thread.sleep(5000);
                        System.out.println("thread1 release");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
        System.out.println("t1 locking");
        // 偏向锁
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
        synchronized (a) {
            System.out.println("main lock");
            // 重量锁
            System.out.println(ClassLayout.parseInstance(a).toPrintable());
        }
        System.out.println("after lock");
        // 重量锁
        System.out.println(ClassLayout.parseInstance(a).toPrintable());
    }
}
```

### 运行结果

<pre><font size = 1>sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           05 00 00 00 (00000101 00000000 00000000 00000000) (5)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
t1 locking
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           05 00 00 00 (00000101 00000000 00000000 00000000) (5)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
thread1 release
main lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           da 1c 17 18 (11011010 00011100 00010111 00011000) (404167898)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
after lock
sync.A object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                               VALUE
      0     4        (object header)                           da 1c 17 18 (11011010 00011100 00010111 00011000) (404167898)
      4     4        (object header)                           00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)                           05 c1 00 20 (00000101 11000001 00000000 00100000) (536920325)
     12     4    int A.i                                       0
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total</font></pre>

### 分析

上面代码在第一次打印对象a的时候，并没有线程去获取他，此时打印出来的标识位为101但是没有偏向任何线程，表示此时对象a处于可偏向状态；

然后第一个线程要进入同步块，对象a升级为偏向锁，同步块中睡眠5s；

在第一个线程还没退出同步块的时候，主线程想要得到锁进入同步块，此时会进行CAS操作，但由于偏向锁还没释放，所以CAS失败，升级为重量锁，将线程挂起等待锁。

从打印结果看出main lock后锁变成重量锁，标识位值为10

## 总结

三种锁出现的场景：

- 偏向锁：一个线程获取锁
- 轻量锁：多个线程交替运行，但没有发生竞争
- 重量锁：多个线程互斥运行，对锁进行竞争




