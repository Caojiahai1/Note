# AbstractQueuedSynchronizer

AQS是AbstractQueuedSynchronizer的简称，它是一个抽象类，是Java并发包的基础工具类。接下来将从ReentrantLock使用AQS源码分析。

## AQS结构

```java
// AQS内部维护了一个双向链表，通过内部类Node来实现
// 头节点，可以理解为当前占用锁的线程节点
private transient volatile Node head;
// 尾节点，头节点后面跟着是一个阻塞队列，这个tail表示这个阻塞队列的队尾，
// 每次有新的线程加入这个阻塞队列，会放到这个队尾
private transient volatile Node tail;
// 用来记录同步状态，0表示当前没有线程占用锁
private volatile int state;

// AQS继承了AbstractOwnableSynchronizer，这个属性是父类定义的
// 用来表示当前持有锁的线程
private transient Thread exclusiveOwnerThread;
```

`Node`结构

```java
static final class Node {
    // 这个是共享锁使用，暂时不分析
    static final Node SHARED = new Node();
    // 这个是独享锁标记，暂时不分析
    static final Node EXCLUSIVE = null;

    // 当waitStatus == CANCELLED是表示线程被取消
    static final int CANCELLED =  1;
    // 当waitStatus == SIGNAL，这个值表示有线程需要当前线程来唤醒
    // 这个值其实和当前线程无关，当有新的线程加入队列，会把自己放到队尾，然后把原来的队尾作为自己的pre节点
    // 因为这个线程加入阻塞队列后就会park，只有等pre节点释放锁后唤醒才能去竞争锁，但是如果你本来就是队尾
    // 没有别的线程需要去唤醒，就不需要去唤醒别人，所以当waitStatus == SIGNAL表示当前线程有next节点需要唤醒
    static final int SIGNAL    = -1;
    // 这个值暂时不分析，和CONDITION锁有关
    static final int CONDITION = -2;

    static final int PROPAGATE = -3;

    // 表示当前线程状态
    volatile int waitStatus;

    // 上一个节点
    volatile Node prev;
	// 下一个节点
    volatile Node next;
	// 当前线程
    volatile Thread thread;

    Node nextWaiter;

    final boolean isShared() {
        return nextWaiter == SHARED;
    }

    final Node predecessor() throws NullPointerException {
        Node p = prev;
        if (p == null)
            throw new NullPointerException();
        else
            return p;
    }

    Node() {    // Used to establish initial head or SHARED marker
    }

    Node(Thread thread, Node mode) {     // Used by addWaiter
        this.nextWaiter = mode;
        this.thread = thread;
    }

    Node(Thread thread, int waitStatus) { // Used by Condition
        this.waitStatus = waitStatus;
        this.thread = thread;
    }
}
```

## ReentrantLock

ReentrantLock内部定义了`private final Sync sync;`，Sync是ReentrantLock内部一个继承了AQS的抽象类，ReentrantLock两个内部类FairSync和NonfairSync实现了Sync抽象类，这两个类实现了公平锁和非公平锁功能。

### 公平锁

使用代码示例

```java
public static void main(String[] args) {
    // ReentrantLock默认是非公平锁，这边传true会创建公平锁
    ReentrantLock lock = new ReentrantLock(true);
    lock.lock();
    System.out.println("公平锁");
    lock.unlock();
}
```

#### lock

这边调用lock方法会进入FairSync的lock方法

```java
static final class FairSync extends Sync {
    private static final long serialVersionUID = -3000897897090466540L;

    // 首先会进入这个lock方法，调用AQS共用方法acquire，这个方法这边直接贴在下面了
    final void lock() {
        acquire(1);
    }
    
    // AQS公共方法
    public final void acquire(int arg) {
        // 首先调用tryAcquire去尝试获取锁，这边进入子类FairSync的tryAcquire
        // 如果获取锁成功，则不再往下执行直接返回
        // 如果获取锁失败，则往下执行，将当前线程封装到一个Node中加入阻塞队列
        // addWaiter方法也是AQS公有方法，直接贴过来
        // addWaiter实际上完成了将当前线程封装到一个Node里面，并将这个Node置为队尾
        // 完成addWaiter后就调用acquireQueued将当前线程park让出CPU
        // acquireQueued方法是一个阻塞方法，知道线程获取到锁继续往下执行
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    
    /**
         * Fair version of tryAcquire.  Don't grant access unless
         * recursive call or no waiters or is first.
         */
    protected final boolean tryAcquire(int acquires) {
        // 获取当前线程
        final Thread current = Thread.currentThread();
        // 获取state状态
        int c = getState();
        // 如果c==0说明当前没有线程占有锁
        if (c == 0) {
            // 首先调用hasQueuedPredecessors判断当前线程前面是否还有其它线程在排队等待获取锁
            // 有则直接返回判断不往下执行，因为前面有线程在阻塞队列中，需要排队
            // 如果返回false，说明接下来就是当前线程阻塞等待获取锁，那么执行进行CAS先尝试一次获取锁
            // 成功则将当前线程设为AQS当前占有锁的线程，不成功则返回false加入队列
            if (!hasQueuedPredecessors() &&
                compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        // 到这一步说明c！=0，当前有线程占有锁了，如果是当前线程占有的，则进行锁重入c的值＋1
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        // 如果有线程已经占有锁，并且不是当前线程，则获取锁失败，返回false
        return false;
    }
    
    // AQS公共方法
    // 这个方法实际上是判断在当前线程片面是否还有其它线程在阻塞等待获取锁
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        // 如果tail == head说明当前没有阻塞队列，所以直接返回false
        // 如果tail ！= head，则往下进行判断s是头节点的下一个节点，实际上指的是阻塞队列的第一个
        // 如果s==null说明head节点next不再指向任何节点，那么只有一种情况head节点释放了锁
        // 当前的head节点已经换成另外一个节点了，所以直接返回true不再去尝试获取锁
        // s！=null则当前head还没释放锁，那么判断s节点的线程是否就是当前线程，如果是当前线程，那么返回false直接去尝试获取锁
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }
    
    // AQS公共方法
    private Node addWaiter(Node mode) {
        // 将当前线程创建一个Node
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        // 将原来的队尾节点定义为当前线程节点的Pre节点
        Node pred = tail;
        // 如果队尾节点不为空，说明阻塞队列已经初始化过了
        if (pred != null) {
            // 将原来的队尾节点设为pre节点
            node.prev = pred;
            // 通过CAS将当前线程节点放到队尾
            if (compareAndSetTail(pred, node)) {
                // CAS成功，那么就将原来的队尾节点next指向当前线程节点
                pred.next = node;
                return node;
            }
        }
        // 走到这一步，说明两种情况
        // 1、原来的tail为空，还没初始化阻塞队列
        // 2、在上一个判断中，将当前节点CAS放到队尾失败
        // 进入enq方法源码看具体做了什么
        enq(node);
        return node;
    }
    
    // AQS公共方法
    private Node enq(final Node node) {
        // 这边是个死循环，自旋（方法实际上通过CAS和自旋将当前节点放到队尾，具体看下面分析）
        for (;;) {
            // 上面说过，有两种情况会进入这个方法，所以这边又对队尾节点进行判断
            Node t = tail;
            // 如果队尾为空，说明阻塞队列为空，这边进行CAS新建一个空节点设为队头，并将其也设为队尾
            // 为什么这边要new一个thread==null的节点放队头？
            // 前面说过队头实际上指的是当前占有锁的线程，Node中存放thread对象是为了在线程被唤醒后
            // 需要知道具体唤醒哪一个thread，队头的线程已经处于运行状态不需要唤醒，所以直接设为null方便GC
            if (t == null) { // Must initialize
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                // 如果队尾不为空进入这个判断，这边逻辑其实和上面CAS将当前节点放到队尾一样
                // 只是这边如果失败会进行自旋重试，直到CAS成功再返回
                // 总结：其实不管进入这个方法的时候，队尾tail是否为空，最终都会走这一步，
                // 只不过tail为空的话，加了一步上面判断的初始化，然后下一个循环就会进入这一步
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }
    
    // AQS公共方法
    final boolean acquireQueued(final Node node, int arg) {
        // 
        boolean failed = true;
        try {
            // 这个参数在这边实际上没什么用，可以忽略
            boolean interrupted = false;
            for (;;) {
                // 获取当前线程的pre节点
                final Node p = node.predecessor();
                // 首先判断前节点是否是头节点，即判断当前线程是不是阻塞队列的第一个
                // 如果是，则尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    // 如果当前获取到锁，则将当前线程节点设为头节点
                    // 进入setHead，会发现内部执行了下面这两行代码
                    // node.thread = null;  将thread置为null，使thread不再被引用方便gc
                    // node.prev = null; 将当前节点不再指向pre节点
                    // 结合下面的p.next = null;将pre节点的next指向null
                    // 这两步使pre节点即原来的头节点不再指向别人和被别人指向，方便gc
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                // 到这一步，说明当前加入队列的线程不是阻塞队列第一个
                // 或者上一个占有锁的线程还持有着锁，当前线程获取锁失败
                // 首先调用shouldParkAfterFailedAcquire方法判断当前线程是否可以park
                // 具体逻辑到代码内部看，当返回true就会往下执行，将当前线程park
                // 注意：这边的park会被Interrupt方法唤醒，但是唤醒后继续循环，
                // 如果还没有轮到当前去竞争锁，或者竞争失败则会继续park，所以lock方法是不能响应Interrupt中断的
                // park后，如果pre节点线程释放了锁，则会唤醒当前线程，继续循环去竞争锁
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
    
    // AQS公共方法
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        // 获取pre节点的waitStatus值
        int ws = pred.waitStatus;
        // 如果pre节点ws==-1，表示pre节点在释放锁后需要执行唤醒下一个节点的操作
        // 实际上第一次循环进来的时候，肯定是ws==0，因为pre节点的这个状态初始化后一直没有修改过
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
        // ws>0 说明pre节点被取消了
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            // 因为pre节点已经被取消了，所以pre节点不会再去获取释放锁
            // 所以如果还是使用这个节点作为pre节点的话，当前线程就永远不会被唤醒了
            // 所以下面逻辑就会往前找，直到找到一个不是取消状态的节点作为当前线程的pre节点
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            // 到这一步，暂时只考虑ws==0，这边会进行CAS将pre节点的ws修改为-1
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        // 这个方法内部只有但ws==-1的时候返回true，将当前线程park
        // 否则会一直返回false，自旋+CAS，直到将pre节点的ws修改为-1
        return false;
    }

}
```



#### unlock

解锁实际上就是调用AQS内部的release方法

```java
// AQS公共方法
public final boolean release(int arg) {
    // 这边会调用ReentrantLock内部重写方法，用来判断锁是否释放成功
    if (tryRelease(arg)) {
        Node h = head;
        // 这边判断是否需要去唤醒其它线程
        // 如果h==null，说明阻塞队列没有被初始化过，所以根本没有线程在阻塞需要被唤醒
        // 如果h！=null，那么判断waitStatus
        // 如果waitStatus==0，说明没有别的操作修改过当前节点的waitStatu状态
        // 前面lock的时候说过，当一个线程节点加入阻塞队列的时候，会把前一个节点waitStatu改为-1，
        // 说明阻塞队列是空的，没有线程需要被唤醒
        // 如果waitStatus != 0，那么进入unparkSuccessor方法看具体做了什么
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

// ReentrantLock方法
protected final boolean tryRelease(int releases) {
    // 将state-1
    int c = getState() - releases;
    // 如果解锁的不是占有锁的线程则直接抛出异常
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    // 标记锁是否是否成功
    boolean free = false;
    // 如果c==0，则锁释放成功
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    // 因为锁有可能重入，所以c的值是>1的，这边将-1后的c重新赋值，只有等重入锁都释放了才能释放成功
    setState(c);
    return free;
}
// AQS公共方法
private void unparkSuccessor(Node node) {
    // 获取头节点waitStatus
    int ws = node.waitStatus;
    // 如果waitStatus<0,首先进行CAS将头节点waitStatus置为0
    // 这一步没有实际意义，可能是为了防止其它地方在操作当前头节点吧
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);

    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        // 如果发现下一个节点不存在或者线程已经被取消了，那么进行下面for循环，从队列的队尾往前遍历
        // 直到遍历到头节点自己结束，将离头节点最近的一个没有取消的节点重新作为头节点的下一个节点，
        // 然后进入下面一个if唤醒该节点的线程
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    // 这边两个if都会执行到
    // 如果下一个节点存在，则直接唤醒下一个节点的线程
    if (s != null)
        LockSupport.unpark(s.thread);
}
```



### 非公平锁

使用示例

```java
public static void main(String[] args) {
    // 使用默认构造方法，或者传false创建非公平锁
    ReentrantLock lock = new ReentrantLock(false);
    lock.lock();
    System.out.println("非公平锁");
    lock.unlock();
}
```

#### lock

非公平锁代码和公平锁代码差不多，这边之分析一下和公平锁的不同之处

```java
static final class NonfairSync extends Sync {
    private static final long serialVersionUID = 7316153563782823691L;

    /**
         * Performs lock.  Try immediate barge, backing up to normal
         * acquire on failure.
         */
    final void lock() {
        // 这边是和公平锁的第一个区别，进来不管是否有锁占有了锁直接就会进行一次CAS去获取锁
        // 这种做法当没有线程占有锁的时候直接获取到锁，省了判断state==0这一步，可以提高性能
        // 但是如果有线程占用了锁，这一步必然会失败，实际上相对于公平锁多执行了一步CAS，不过CAS对性能的影响可以忽略
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }

    // 非公平锁tryAcquire竞争锁的方法也和公平锁有不同，具体看nonfairTryAcquire方法源码
    protected final boolean tryAcquire(int acquires) {
        return nonfairTryAcquire(acquires);
    }
    
    // 非公平锁获取锁方法
    final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                // 非公平锁在这一步CAS获取锁的时候，没有判断当前线程前面是否还有其它线程在排队获取锁，直接去尝试竞争锁
                // 竞争成功，则获取到锁
                // 竞争失败，则下面逻辑和公平锁一样，会加入到阻塞队列队尾park等待唤醒
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
}
```

#### unlock

解锁过程和公平锁一样

### 总结

非公平锁和公平锁只在加锁的时候存在区别，非公平锁在进入lock方法不管当前是否有没有线程占用锁直接进行一次CAS获取锁，如果获取失败了和公平锁一样进入acquire方法，当c==0即当前没有线程占有锁了，则会去竞争锁，公平锁会先判断在当前线程前面是否有其它线程在阻塞队列中排队获取锁，非公平锁则不管前面是否有线程在排队获取锁，直接进行CAS去竞争锁。



## ReentrantReadWriteLock

ReentrantReadWriteLock是读写锁。

读写锁原则：读读并行、读写串行、写写串行，即只要有写操作，就会串行。

应用示例：

```java
package thread;

import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Yan liang
 * @create 2019/8/2
 * @since 1.0.0
 */
public class ReadWriteLockTest {

    static int a =0;

    public static void main(String[] args) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        for (int i = 0; i < 20; i++) {
            if (i % 2 == 0) {
                Thread thread = new Thread("thread" + i){
                    @Override
                    public void run() {
                        readWriteLock.readLock().lock();
                        System.out.println(Thread.currentThread().getName() + "开始读");
                        System.out.println(Thread.currentThread().getName() + "读" + a);
                        readWriteLock.readLock().unlock();
                    }
                };
                thread.start();
                continue;
            }
            Thread thread = new Thread("thread" + i){
                @Override
                public void run() {
                    readWriteLock.writeLock().lock();
                    System.out.println(Thread.currentThread().getName() + "开始写");
                    a = new Random().nextInt(20);
                    System.out.println(Thread.currentThread().getName() + "写" + a);
                    readWriteLock.writeLock().unlock();
                }
            };
            thread.start();
        }
    }
}
```

打印结果

```java
thread0开始读
thread0读0
thread7开始写
thread7写10
thread1开始写
thread1写14
thread3开始写
thread3写8
thread4开始读
thread4读8
// 读读并行
thread6开始读
thread2开始读
thread2读8
thread6读8
thread5开始写
thread5写17
thread8开始读
thread8读17
thread9开始写
thread9写8
// 读读并行
thread10开始读
thread12开始读
thread10读8
thread12读8
thread13开始写
thread13写6
thread11开始写
thread11写5
thread14开始读
thread14读5
thread15开始写
thread15写1
thread16开始读
thread16读1
thread17开始写
thread17写18
thread18开始读
thread18读18
thread19开始写
thread19写7
```

从打印结果可以看出，有两处读线程是串行的，所有写线程都会保证原子性，所以当有写线程持有锁的时候，所有线程都是串行的。

读写锁的升级和降级，升级指的是从读锁升级到写锁，降级指的是从写锁降级为读锁。实际上锁的升级是有问题的，有可能会造成死锁，如果有多个读线程占有一把锁，当这几个线程同时升级为写锁时，因为写锁是独占锁，所以要想占有写锁就必须等其它线程把锁释放，然而这时候这几把锁都在等待其它线程释放锁后升级为写锁，所以会造成死锁。

### 写锁

#### lock

读写锁也分为公平锁和非公平锁，这边就不分开讨论非公平锁和公平锁，直接会在两者有区别的地方指出。

读写锁内部也维护了一个Sync抽象类继承了AQS

```java
// ReentrantReadWriteLock内部维护了一个内部类WriteLock
// WriteLock的加锁方式和ReentrantLock加锁方式类似，都属于独占锁，进入acquire方法查看
public void lock() {
    sync.acquire(1);
}

// 这个方法是AQS的公共方法，进入Sync内部的tryAcquire方法
// 这边就分析tryAcquire方法，其它方法和ReentrantLock一样，都是用的AQS公共方法
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

// ReentrantReadWriteLock内部类Sync内部定义的几个属性
static final int SHARED_SHIFT   = 16;
// 1左移16位，0000000000000001 0000000000000000
static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
// 1左移16位后减1，0000000000000000 1111111111111111
static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
// 1左移16位后减1，0000000000000000 1111111111111111
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

// ReentrantReadWriteLock内部类Sync方法
protected final boolean tryAcquire(int acquires) {
    Thread current = Thread.currentThread();
    // 获取当前锁的状态
    // 读写锁把读锁和写锁的状态，分别记录在c值的高16位和低16位上
    int c = getState();
    // static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }
    // 这是这个方法内部代码，假设此时有1个线程占用了锁
    // 1、如果是写锁则c = 0000000000000000 0000000000000001，w = 0000000000000000 0000000000000001
    // 2、如果是读锁则c = 0000000000000001 0000000000000000，w = 0000000000000000 0000000000000000
    // 如果没有线程占有锁，那么w肯定等于0
    int w = exclusiveCount(c);
    // 当有锁占有线程的时候
    if (c != 0) {
        // (Note: if c != 0 and w == 0 then shared count != 0)
        // 如果w==0，说明占有锁的线程为读锁，由于读写锁不能并行，所以直接返回false进入阻塞队列
        // 如果w!=0，说明占有锁的线程为写锁，那么就判断是不是当前线程占有，如果是那么进入下面逻辑可以重入锁。
        if (w == 0 || current != getExclusiveOwnerThread())
            return false;
        if (w + exclusiveCount(acquires) > MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        // Reentrant acquire
        setState(c + acquires);
        return true;
    }
    // writerShouldBlock其实内部就是调用了AQS方法hasQueuedPredecessors（上面已经分析过）
    // 判断当前线程前面是否还有其它线程在排队等待获取锁
    // 这边也是公平锁和非公平锁的区别，公平锁会进行判断，非公平锁则直接返回false进行CAS尝试获取锁
    if (writerShouldBlock() ||
        !compareAndSetState(c, c + acquires))
        return false;
    setExclusiveOwnerThread(current);
    return true;
}
```

#### unlock

```java
// 解锁过程和ReentrantLock类似，主要区别在tryRelease方法，所以直接看这个方法
protected final boolean tryRelease(int releases) {
    // 判断是不是当前持有锁的线程释放锁，不是则抛出异常
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    // 将写锁（写锁在低16位）的值减1
    int nextc = getState() - releases;
    // 这一步是判断写锁是否减为0了，大于0说明有写锁重入，=0那么就可以直接释放写锁
    boolean free = exclusiveCount(nextc) == 0;
    if (free)
        setExclusiveOwnerThread(null);
    setState(nextc);
    return free;
}
```



### 读锁

#### lock

```java
// ReentrantReadWriteLock内部维护了一个内部类ReadLock
// ReadLock调用的是acquireShared的方法
public void lock() {
    sync.acquireShared(1);
}

// AQS获取共享锁方法
public final void acquireShared(int arg) {
    // 进入写锁tryAcquireShared方法，<0 返回false说明获取锁成功直接返回
    // 返回true说明获取锁失败，进入doAcquireShared方法将当前线程放入队列
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}

// 共享锁（读锁）
protected final int tryAcquireShared(int unused) {
    Thread current = Thread.currentThread();
    int c = getState();
    // 如果exclusiveCount(c) != 0，说明有写线程占用了锁
    // 判断是否是当前线程占有，如果是则进入下面逻辑看是否能降级重入锁，不是则直接返回-1
    if (exclusiveCount(c) != 0 &&
        getExclusiveOwnerThread() != current)
        return -1;
    // static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
    // 上面是sharedCount代码，将c值右移16位取的读锁的值
    int r = sharedCount(c);
    // 这边readerShouldBlock方法，如果是公平锁和写锁一样，内部调用了hasQueuedPredecessors方法
    // 如果是非公平锁，内部调用了apparentlyFirstQueuedIsExclusive方法
    // 这个方法内部判断，如果存在下一个等待获取锁的线程不是一个共享锁线程，那么返回true，不进行CAS
    // 否则如果也是一个共享锁线程在等待获取锁，那么直接进行CAS
    if (!readerShouldBlock() &&
        // 这边只是限制锁被共享占有的次数不能大于这个最大值
        r < MAX_COUNT &&
        compareAndSetState(c, c + SHARED_UNIT)) {
        // CAS成功，如果r==0，说明是第一个线程来获取锁，这边直接将这个线程和它重入数量记录在两个属性副本中
        if (r == 0) {
            firstReader = current;
            firstReaderHoldCount = 1;
        } else if (firstReader == current) {
            // 因为第一个线程是记录在firstReader中的，这边可以直接对其重入值++
            firstReaderHoldCount++;
        } else {
            // 如果有别的线程来占有共享锁
            // HoldCounter对象维护了一个线程id和线程重入数量count
            // cachedHoldCounter是这边定义了一个缓存副本，用来记录上一次非first的线程信息
            HoldCounter rh = cachedHoldCounter;
            // 如果rh == null说明除了第一个线程外，还没有其它线程占有共享锁
            // 这边会将当前线程保存到一个ThreadLocal本地变量中，并赋值给缓存
            // 如果下一次还是这个线程来获取锁，那么直接冲缓存中取
            // 如果下一次是另外一个线程来获取锁，那么会从ThreadLocalMap中获取，并重新赋值给缓存
            if (rh == null || rh.tid != getThreadId(current))
                cachedHoldCounter = rh = readHolds.get();
            // 这边rh.count==0是因为有可能解锁时将这个值减为0
            else if (rh.count == 0)
                readHolds.set(rh);
            rh.count++;
        }
        // 这边再CAS操作成功后实际上就是将占有锁的线程信息保存起来，但是如果是第一个线程和上一个线程来，
        // 可以直接从副本变量和缓存中获取，提高性能
        return 1;
    }
    // 因为当前线程已经满足去占有锁的条件，所以会进入这个方法自旋+CAS获取锁
    return fullTryAcquireShared(current);
}

final int fullTryAcquireShared(Thread current) {
    HoldCounter rh = null;
    for (;;) {
        int c = getState();
        // 这边和上面方法一样，首先判断是否可以去获取锁
        if (exclusiveCount(c) != 0) {
            if (getExclusiveOwnerThread() != current)
                return -1;
            // else we hold the exclusive lock; blocking here
            // would cause deadlock.
        } else if (readerShouldBlock()) {
            // Make sure we're not acquiring read lock reentrantly
            if (firstReader == current) {
                // 第一个线程进到这边firstReaderHoldCount肯定>0
                // assert firstReaderHoldCount > 0;
            } else {
                if (rh == null) {
                    rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current)) {
                        rh = readHolds.get();
                        if (rh.count == 0)
                            readHolds.remove();
                    }
                }
                // 这边如果当前线程占有锁count==0,那么说明当前线程不是重入锁，所以直接返回进入队列
                if (rh.count == 0)
                    return -1;
            }
        }
        if (sharedCount(c) == MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        // 到这边首先说明当前没有写锁占有锁
        // 1、如果当前线程前面没有线程在排队获取锁，那么肯定直接到这一步获取锁
        // 2、如果有线程在阻塞队列中，那么看当前线程是否能重入，能重入那么到这一步获取锁，否则返回-1
        if (compareAndSetState(c, c + SHARED_UNIT)) {
            if (sharedCount(c) == 0) {
                firstReader = current;
                firstReaderHoldCount = 1;
            } else if (firstReader == current) {
                firstReaderHoldCount++;
            } else {
                if (rh == null)
                    rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                else if (rh.count == 0)
                    readHolds.set(rh);
                rh.count++;
                cachedHoldCounter = rh; // cache for release
            }
            return 1;
        }
    }
}

// AQS将共享锁放入阻塞队列
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    // 这边和独享锁的区别
                    // 如果当前读线程获取到了锁，这一步会将当前线程设置为头节点
                    // 并且会判断，如果当前线程的下一个节点也是共享锁节点，那么直接唤醒
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    if (interrupted)
                        selfInterrupt();
                    failed = false;
                    return;
                }
            }
            // 这边和独占锁一样，将线程挂起等待唤醒
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; // Record old head for check below
    setHead(node);
   // 这边propagate肯定>0,所以进入判断
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
        (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        // 如果下一个节点是null，或者也是共享锁节点进入doReleaseShared方法
        if (s == null || s.isShared())
            doReleaseShared();
    }
}

// 唤醒下一个共享锁节点
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            // ws==-1说明有节点需要被唤醒
            if (ws == Node.SIGNAL) {
                // 这边会一直循环直到CAS修改ws为0成功
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            // loop to recheck cases
                // CAS成功，去唤醒下一个节点
                unparkSuccessor(h);
            }
            else if (ws == 0 &&
                     // 如果ws==0，则把ws通过CAS修改为-3
                     !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                // loop on failed CAS
        }
        // 如果head节点发生了变化，那么继续循环
        if (h == head)                   // loop if head changed
            break;
    }
}
```



#### unlock

```java
// 读锁释放锁调用的是releaseShared方法
public void unlock() {
    sync.releaseShared(1);
}

// AQS释放共享锁方法
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        // 这个方法可以看上面解析，唤醒下一个读锁节点
        doReleaseShared();
        return true;
    }
    return false;
}

protected final boolean tryReleaseShared(int unused) {
    Thread current = Thread.currentThread();
    if (firstReader == current) {
        // assert firstReaderHoldCount > 0;
        // 如果是第一个获取共享锁的线程，那么firstReaderHoldCount肯定>0
        // 如果firstReaderHoldCount==1，那么第一个线程就完全释放锁了，这边直接将firstReader=null
        if (firstReaderHoldCount == 1)
            firstReader = null;
        else
            firstReaderHoldCount--;
    } else {
        // rh取出当前线程信息，count<=1直接将当前线程副本remove掉
        HoldCounter rh = cachedHoldCounter;
        if (rh == null || rh.tid != getThreadId(current))
            rh = readHolds.get();
        int count = rh.count;
        if (count <= 1) {
            readHolds.remove();
            if (count <= 0)
                throw unmatchedUnlockException();
        }
        --rh.count;
    }
    for (;;) {
        int c = getState();
        // 将读锁c高16位减一
        int nextc = c - SHARED_UNIT;
        if (compareAndSetState(c, nextc))
            // Releasing the read lock has no effect on readers,
            // but it may allow waiting writers to proceed if
            // both read and write locks are now free.
            // next==0，说明读锁和写锁全都释放了，此时才返回true释放锁成功
            return nextc == 0;
    }
}
```

