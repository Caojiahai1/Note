[TOC]

## synchronized关键字

### 应用

synchronized关键字锁定的是对象不是代码块；

锁定的对象有两种：1、类的实例 2、类对象（类锁）

类普通方法上加synchronized关键字，锁定当前类的实例，注意使用单例模式保证线程安全。

类静态方法上加synchronized关键字，锁定的是当前类对象（.class文件），静态方法不能使用类的实例加锁。

### 使用注意

- 锁对象属性的改变，不影响锁的使用，但如果引用对象发生了改变，则锁对象也发生改变
- 不要以字符串常量作为锁对象，不同的引用对象引用的是同一个字符串常量对象，造成错误
- 加锁的同步代码块语句越少越好

### 重入锁

一个同步方法内调用另一个同步方法，两个方法使用相同的对象锁可以重入。继承的情况，调用父类方法也可以重入。

### notify和notiftAll

这两个方法必须由对象锁调用。

notify：随机唤醒一个线程，具体哪个线程由操作系统决定

notiftAll：唤醒所有等待线程

### 异常

如果线程内发生异常没有进行处理的话，线程会结束线程并释放锁。

## volatile关键字

volatile关键字使变量在所有线程中可见，并避免发生指令重排，不能保证原子性。

## ReentrantLock

ReentrantLock可以完成和synchronized关键字同样的功能，但是ReentrantLock必须在finally代码块调用unlock释放锁。

### trylock

该方法可以在指定时间内尝试获取锁，超过等待时间，不管有没有获取到锁代码都将继续执行，如果获取到锁记得在finally代码块调用unlock释放锁。

```java
locked = lock.tryLock(3, TimeUnit.SECONDS);
```

### lockInterruptibly

使用lockInterruptibly加锁，可以响应中断

```java
lock.lockInterruptibly();
```

### 分类

公平锁：根据等待时间依次获取锁

非公平锁：每次加锁的时候都会尝试去竞争锁