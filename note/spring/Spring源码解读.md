# Spring源码解读

## 定义

### DefaultListableBeanFactory

DefaultListableBeanFactory是一个beanFactory容器，内部放了许多属性

#### beanDefinitionMap（bdMap）

beanDefinitionMap是一个key为beanName，value为BeanDefinition的Map容器，它存放了Spring所有bean的描述信息。

### BeanDefinition

用来描述一个bean所有信息的类，里面存储了包括BeanClass,Scope,Lazy,dependsOn等等用来描述bean的属性。

RootBeanDefinition、AnnotatedGenericBeanDefinition等这些类实现了BeanDefinition接口，分别用来表示Spring自己定义的Bean和加了注解的Bean



## 主线流程

Appconfig为自定义的配置类，下面代码启动Spring

```java
AnnotationConfigApplicationContext configApplicationContext = new AnnotationConfigApplicationContext(Appconfig.class);
```

AnnotationConfigApplicationContext继承了GenericApplicationContext类，初始化的时候首先调用父类GenericApplicationContext的构造函数，在Spring环境中初始化了DefaultListableBeanFactory做为bean工厂。

```java
public GenericApplicationContext() {
    this.beanFactory = new DefaultListableBeanFactory();
}
```

然后调用自身构造函数，整体分为四步

- 初始化一个BD读取器
- 初始化一个扫描器，用来扫描BD（这个扫描器在spring自己初始化过程中没有用到，提供用户自己调用scan方法时使用）
- 调用register方法，将传入的配置类信息读取出来，放入beanFactory中
- 最后进入核心方法refresh

```java
public AnnotationConfigApplicationContext() {
    this.reader = new AnnotatedBeanDefinitionReader(this);
    this.scanner = new ClassPathBeanDefinitionScanner(this);
}
public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
    this();
    register(annotatedClasses);
    refresh();
}
```

### AnnotatedBeanDefinitionReader

初始化bd读取器主要传了BeanDefinitionRegistry和Environment两个参数

这边主要看BeanDefinitionRegistry，是用来注册bd到beanFactory中的工具类。从上面代码可以发现BeanDefinitionRegistry传的是this，即AnnotationConfigApplicationContext的实例对象，因为其父类GenericApplicationContext实现了BeanDefinitionRegistry接口

```java
public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
    Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
    Assert.notNull(environment, "Environment must not be null");
    this.registry = registry;
    this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
    AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
}
```

完成属性初始化后，最后一行代码使用这个registry，将Spring内部定义的一些类注册到bean工厂的bdMap中。

主要包括ConfigurationClassPostProcessor、AutowiredAnnotationBeanPostProcessor、RequiredAnnotationBeanPostProcessor等类，这些类在后面配置bean工厂时会使用。

### ClassPathBeanDefinitionScanner

### register

这个方法主要是将传入的配置类信息读取出来，放入到beanFactory中

```java
<T> void doRegisterBean(Class<T> annotatedClass, @Nullable Supplier<T> instanceSupplier, @Nullable String name,
			@Nullable Class<? extends Annotation>[] qualifiers, BeanDefinitionCustomizer... definitionCustomizers) {

    // 创建BD
    AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(annotatedClass);
    // 这边判断如果没有注解元数据或者加了@Conditional注解，则直接返回
    if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
        return;
    }

    // 这边传进来为null
    abd.setInstanceSupplier(instanceSupplier);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
    // 设置scope
    abd.setScope(scopeMetadata.getScopeName());
    // 使用Spring默认的beanName生成器生成beanName
    String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

    // 这个方法内部主要是将注解元数据属性（Lazy、Primary、DependsOn、Role、Description）赋值到abd上
    AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
    // 这边传进来是null
    if (qualifiers != null) {
        for (Class<? extends Annotation> qualifier : qualifiers) {
            if (Primary.class == qualifier) {
                abd.setPrimary(true);
            }
            else if (Lazy.class == qualifier) {
                abd.setLazyInit(true);
            }
            else {
                abd.addQualifier(new AutowireCandidateQualifier(qualifier));
            }
        }
    }
    // 这边传进来是null
    for (BeanDefinitionCustomizer customizer : definitionCustomizers) {
        customizer.customize(abd);
    }

    // bd和beanName封装成一个bdHolder，主要是为了方便传参
    BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
    definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
    // 将bd注册到beanFactory中（put进bdMap中）
    BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
}
```



### refresh

```java
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // Prepare this context for refreshing.
        // 准备工作包括设置启动时间，是否激活标识位，初始化属性源(property source)配置
        prepareRefresh();

        // Tell the subclass to refresh the internal bean factory.
        // 获取beanFactory,需要对beanFactory进行配置
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // Prepare the bean factory for use in this context.
        // 准备beanFactory，具体看下面进入方法体代码
        prepareBeanFactory(beanFactory);

        try {
            // Allows post-processing of the bean factory in context subclasses.
            // 暂时是个空壳方法，可能Spring后续版本会进行实现
            postProcessBeanFactory(beanFactory);

            // Invoke factory processors registered as beans in the context.
            // 这个方法内部会执行Spring自己定义的BeanFactoryPostProcessors
            // 和用户手动添加的BeanFactoryPostProcessors
            // 具体进入方法体看
            invokeBeanFactoryPostProcessors(beanFactory);

            // Register bean processors that intercept bean creation.
            // 取出所有的后置处理器进行排序，并放入到beanFactory中
            registerBeanPostProcessors(beanFactory);

            // Initialize message source for this context.
            // 国际化
            initMessageSource();

            // Initialize event multicaster for this context.
            initApplicationEventMulticaster();

            // Initialize other special beans in specific context subclasses.
            // 空壳方法
            onRefresh();

            // Check for listener beans and register them.
            // 注册一些监听，这边不做分析
            registerListeners();

            // Instantiate all remaining (non-lazy-init) singletons.
            // 这个方法完成了单例bean的实例化和依赖的注入
            finishBeanFactoryInitialization(beanFactory);

            // Last step: publish corresponding event.
            finishRefresh();
        }

        catch (BeansException ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Exception encountered during context initialization - " +
                            "cancelling refresh attempt: " + ex);
            }

            // Destroy already created singletons to avoid dangling resources.
            destroyBeans();

            // Reset 'active' flag.
            cancelRefresh(ex);

            // Propagate exception to caller.
            throw ex;
        }

        finally {
            // Reset common introspection caches in Spring's core, since we
            // might not ever need metadata for singleton beans anymore...
            resetCommonCaches();
        }
    }
}
```

#### prepareBeanFactory

```java
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // Tell the internal bean factory to use the context's class loader etc.
    // 设置类加载器
    beanFactory.setBeanClassLoader(getClassLoader());
    // 添加一个表达式解析器，用来解析bean表达式
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

    // Configure the bean factory with context callbacks.
    // 添加一个后置处理器，这后置处理器用来添加各种实现了*Aware类型接口的类
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
    // 添加忽略列表，表示实现下面这些接口的类不会注入到Spring容器中
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

    // BeanFactory interface not registered as resolvable type in a plain factory.
    // MessageSource registered (and found for autowiring) as a bean.
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // Register early post-processor for detecting inner beans as ApplicationListeners.
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

    // Detect a LoadTimeWeaver and prepare for weaving, if found.
    if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
        // Set a temporary ClassLoader for type matching.
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }

    // Register default environment beans.
    // 下面三个判断beanFactory是否已经包含了这几个默认beanName的bean，如果没有则添加
    if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
    }
}
```

#### invokeBeanFactoryPostProcessors

主要看进入下面这行方法内部

```java
// getBeanFactoryPostProcessors()方法获取的是用户手动添加的BeanFactoryPostProcessor
// 手动添加示例：configApplicationContext.addBeanFactoryPostProcessor(...);
PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());
```

这边有两个接口BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor，BeanDefinitionRegistryPostProcessor实际上继承了BeanFactoryPostProcessor，添加了一个扩展方法

```java
public static void invokeBeanFactoryPostProcessors(
    ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

    // Invoke BeanDefinitionRegistryPostProcessors first, if any.
    Set<String> processedBeans = new HashSet<>();

    // 首先判读beanFactory是否属于BeanDefinitionRegistry，
    // 当前beanFactory DefaultListableBeanFactory实现了BeanDefinitionRegistry，所以进入代码块
    if (beanFactory instanceof BeanDefinitionRegistry) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        // 这个list用来存放用户手动添加的只实现了BeanFactoryPostProcessor的类
        List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
        // 这个list用来存放用户手动添加的实现了BeanDefinitionRegistryPostProcessor的类
        List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

        // 循环用户手动添加beanFactoryPostProcessors，放入到对应list
        for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
            if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                BeanDefinitionRegistryPostProcessor registryProcessor =
                    (BeanDefinitionRegistryPostProcessor) postProcessor;
                // 如果实现了BeanDefinitionRegistryPostProcessor接口，这边直接执行其扩展方法postProcessBeanDefinitionRegistry
                registryProcessor.postProcessBeanDefinitionRegistry(registry);
                registryProcessors.add(registryProcessor);
            }
            else {
                regularPostProcessors.add(postProcessor);
            }
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        // Separate between BeanDefinitionRegistryPostProcessors that implement
        // PriorityOrdered, Ordered, and the rest.
        // 这个list用来存放Spring自己定义的实现BeanDefinitionRegistryPostProcessor接口的类
        List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

        // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
        // 根据类型BeanDefinitionRegistryPostProcessor从beanFactory取出beanNames
        String[] postProcessorNames =
            beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
        // 循环beanNames将Spring定义的放入currentRegistryProcessors中
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                processedBeans.add(ppName);
            }
        }
        // 排序
        sortPostProcessors(currentRegistryProcessors, beanFactory);
        // 合并
        registryProcessors.addAll(currentRegistryProcessors);
        // 这个方法先执行Spring自定义的BeanDefinitionRegistryPostProcessors，执行其对BeanFactoryPostProcessor的扩展方法postProcessBeanDefinitionRegistry
        invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
        currentRegistryProcessors.clear();

        // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
        // 接下来代码和上面差不多，一般不会再取到BeanDefinitionRegistryPostProcessors来执行了，SPring可能处于严谨考虑什么的，又去取了几次
        postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
        for (String ppName : postProcessorNames) {
            if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                processedBeans.add(ppName);
            }
        }
        sortPostProcessors(currentRegistryProcessors, beanFactory);
        registryProcessors.addAll(currentRegistryProcessors);
        invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
        currentRegistryProcessors.clear();

        // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
        boolean reiterate = true;
        while (reiterate) {
            reiterate = false;
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName)) {
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    processedBeans.add(ppName);
                    reiterate = true;
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            currentRegistryProcessors.clear();
        }

        // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
        // 下面两个方法是执行BeanFactoryPostProcessor接口的实现方法postProcessBeanFactory
        invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
    }

    else {
        // Invoke factory processors registered with the context instance.
        invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
    }

    // Do not initialize FactoryBeans here: We need to leave all regular beans
    // uninitialized to let the bean factory post-processors apply to them!
    String[] postProcessorNames =
        beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

    // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
    // Ordered, and the rest.
    List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
    List<String> orderedPostProcessorNames = new ArrayList<>();
    List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    for (String ppName : postProcessorNames) {
        if (processedBeans.contains(ppName)) {
            // skip - already processed in first phase above
        }
        else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
            priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
        }
        else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
            orderedPostProcessorNames.add(ppName);
        }
        else {
            nonOrderedPostProcessorNames.add(ppName);
        }
    }

    // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
    sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

    // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
    List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
    for (String postProcessorName : orderedPostProcessorNames) {
        orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    sortPostProcessors(orderedPostProcessors, beanFactory);
    invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

    // Finally, invoke all other BeanFactoryPostProcessors.
    List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
    for (String postProcessorName : nonOrderedPostProcessorNames) {
        nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
    }
    invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

    // Clear cached merged bean definitions since the post-processors might have
    // modified the original metadata, e.g. replacing placeholders in values...
    beanFactory.clearMetadataCache();
}
```

invokeBeanFactoryPostProcessors这个方法实际上是对实现了BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor这两个接口的类调用其扩展方法；

Spring内部提供了ConfigurationClassPostProcessor这个类，这个类是在初始化BD读取器的时候放到bdMap中的，ConfigurationClassPostProcessor类的具体分析到重点类中查看。

#### finishBeanFactoryInitialization

这个方法完成了bean的实例化，bean之间的循环依赖，后置处理器、实现Aware接口等生命周期方法的回调。

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // Initialize conversion service for this context.
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
        beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
            beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }

    // Register a default embedded value resolver if no bean post-processor
    // (such as a PropertyPlaceholderConfigurer bean) registered any before:
    // at this point, primarily for resolution in annotation attribute values.
    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }

    // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }

    // Stop using the temporary ClassLoader for type matching.
    beanFactory.setTempClassLoader(null);

    // Allow for caching all bean definition metadata, not expecting further changes.
    beanFactory.freezeConfiguration();

    // Instantiate all remaining (non-lazy-init) singletons.
    // 主要看这个方法，上面可以忽略
    beanFactory.preInstantiateSingletons();
}

public void preInstantiateSingletons() throws BeansException {
    if (logger.isDebugEnabled()) {
        logger.debug("Pre-instantiating singletons in " + this);
    }

    // Iterate over a copy to allow for init methods which in turn register new bean definitions.
    // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
    // 取出所有的beanNames
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

    // Trigger initialization of all non-lazy singleton beans...
    // 循环所有beanName
    for (String beanName : beanNames) {
        // 这边是取出合并后的bd，因为在xml配置bean的时候，可以给bean配置一个parentBean
        // 如果有可以给bean配置一个parentBean,就会合并parentbd信息
        // 一般bean都没有parentBean，取出的就是自身的bd
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        // bean不是抽象的并且是单例不是懒加载的，那么进入判断逻辑
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            // 判断是否是factoryBean
            if (isFactoryBean(beanName)) {
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                if (bean instanceof FactoryBean) {
                    final FactoryBean<?> factory = (FactoryBean<?>) bean;
                    boolean isEagerInit;
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
                                                                    ((SmartFactoryBean<?>) factory)::isEagerInit,
                                                                    getAccessControlContext());
                    }
                    else {
                        isEagerInit = (factory instanceof SmartFactoryBean &&
                                       ((SmartFactoryBean<?>) factory).isEagerInit());
                    }
                    if (isEagerInit) {
                        getBean(beanName);
                    }
                }
            }
            else {
                // 普通的bean会进入这个getBean方法，这个方法调用AbstractBeanFactory的doGetBean
                getBean(beanName);
            }
        }
    }

    // Trigger post-initialization callback for all applicable beans...
    for (String beanName : beanNames) {
        Object singletonInstance = getSingleton(beanName);
        if (singletonInstance instanceof SmartInitializingSingleton) {
            final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    smartSingleton.afterSingletonsInstantiated();
                    return null;
                }, getAccessControlContext());
            }
            else {
                smartSingleton.afterSingletonsInstantiated();
            }
        }
    }
}

protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

    // 获取转换后的beanName，主要是factoryBean的beanName前面会加&
    final String beanName = transformedBeanName(name);
    Object bean;

    // Eagerly check singleton cache for manually registered singletons.
    // 这边会去先取一次bean实例
    // 这边getSingleton看下面具体分析，因为有两个同名重载方法，所以下面注释为第一个getSingleton
    Object sharedInstance = getSingleton(beanName);
    // 第一次实例化bean的时候sharedInstance肯定==null，直接进入else判断
    if (sharedInstance != null && args == null) {
        if (logger.isDebugEnabled()) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                             "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        // 这边是原型bean判断，这边不用管
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        // 获取父BeanFactory，一般都是为空
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                    nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
        }

        // 这边将当前bean放入alreadyCreated这个Map中，具体作用这边不做分析
        if (!typeCheckOnly) {
            markBeanAsCreated(beanName);
        }

        try {
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            // 判断bd，内部判断bd是否是抽象类型，是的话会抛出异常
            checkMergedBeanDefinition(mbd, beanName, args);

            // Guarantee initialization of beans that the current bean depends on.
            // 获取bd的依赖，这边先不分析，一般都为空
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dep : dependsOn) {
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                        "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    registerDependentBean(dep, beanName);
                    try {
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                        "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }

            // Create bean instance.
            if (mbd.isSingleton()) {
                // 这边调用了getSingleton的另一个重载方法，这个方法看下面第二个getSingleton注释
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            else if (mbd.isPrototype()) {
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    beforePrototypeCreation(beanName);
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            else {
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                                                    "Scope '" + scopeName + "' is not active for the current thread; consider " +
                                                    "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                                                    ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // Check if required type matches the type of the actual bean instance.
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to convert bean '" + name + "' to required type '" +
                             ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}

// 第一个getSingleton
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 首先从singletonObjects中取，如果当前bean还没有完成初始化，那么肯定还没放入这个集合
    Object singletonObject = this.singletonObjects.get(beanName);
    // bean还没初始化完成singletonObject肯定==null
    // 第二个判断是判断当前bean是否在创建当中，第一次进入这个方法肯定返回false，
    // 往下看代码，在bean实例化后还没设置属性之前，会把他放入singletonsCurrentlyInCreation这个Map
    // 这个方法在bean实例化之前，那么这个判断什么时候会是true呢? 这边就要涉及到bean的循环依赖
    // 例：A依赖B，B依赖A，当A实例化后要注入B，可是此时B还没完成初始化从beanFactory中get不到，
    // 实际上注入B时调用了getBean(B),此时就开始初始化B，由于B有需要注入A，所以又会调用getBean(A)，
    // 此时到这个方法的时候isSingletonCurrentlyInCreation(beanName)就会返回true
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        synchronized (this.singletonObjects) {
            // 到这一步的时候是第二次调用getBean(A)，在第一次调用getBean(A)的时候
            // A已经被实例化只是还没设置属性，并且放入到了singletonFactories中
            // 所以earlySingletonObjects中还没有A
            singletonObject = this.earlySingletonObjects.get(beanName);
            if (singletonObject == null && allowEarlyReference) {
                // 从singletonFactories取出singletonObject
                ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                if (singletonFactory != null) {
                    // 从
                    singletonObject = singletonFactory.getObject();
                    this.earlySingletonObjects.put(beanName, singletonObject);
                    this.singletonFactories.remove(beanName);
                }
            }
        }
    }
    return singletonObject;
}

// 第二个getSingleton
public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(beanName, "Bean name must not be null");
    synchronized (this.singletonObjects) {
        Object singletonObject = this.singletonObjects.get(beanName);
        // 这边singletonObject在没有并发的情况下肯定==null
        if (singletonObject == null) {
            if (this.singletonsCurrentlyInDestruction) {
                throw new BeanCreationNotAllowedException(beanName,
                                                          "Singleton bean creation not allowed while singletons of this factory are in destruction " +
                                                          "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
            }
            // 这边就是做了一些验证，不用管
            beforeSingletonCreation(beanName);
            boolean newSingleton = false;
            boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
            if (recordSuppressedExceptions) {
                // 定义一个set用来存放异常
                this.suppressedExceptions = new LinkedHashSet<>();
            }
            try {
                // 这边会调用传入的匿名类的createBean方法
                // 先会调用AbstractAutowireCapableBeanFactory的createBean方法
                // 这个方法首先会调用resolveBeforeInstantiation方法，不做具体分析
                // 这个方法可以通过实现InstantiationAwareBeanPostProcessor自定义实例化一个bean
                // 如果上面这个方法没有返回一个自定义的bean，那么会调用doCreateBean，看下面具体分析
                singletonObject = singletonFactory.getObject();
                newSingleton = true;
            }
            catch (IllegalStateException ex) {
                // Has the singleton object implicitly appeared in the meantime ->
                // if yes, proceed with it since the exception indicates that state.
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    throw ex;
                }
            }
            catch (BeanCreationException ex) {
                if (recordSuppressedExceptions) {
                    for (Exception suppressedException : this.suppressedExceptions) {
                        ex.addRelatedCause(suppressedException);
                    }
                }
                throw ex;
            }
            finally {
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = null;
                }
                afterSingletonCreation(beanName);
            }
            if (newSingleton) {
                // 这边最终将初始化好的bean放入到singletonObjects中
                // 并把singletonFactories和earlySingletonObjects中缓存清掉
                addSingleton(beanName, singletonObject);
            }
        }
        return singletonObject;
    }
}

protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {

    // Instantiate the bean.
    // BeanWrapper是一个对bean进行包装的接口
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        // 从缓存中取，这边取不到
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    if (instanceWrapper == null) {
        // 这个方法完成了bean的实例化，但还没注入bean依赖的属性
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    final Object bean = instanceWrapper.getWrappedInstance();
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
        mbd.resolvedTargetType = beanType;
    }

    // Allow post-processors to modify the merged bean definition.
    synchronized (mbd.postProcessingLock) {
        if (!mbd.postProcessed) {
            try {
                applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            }
            catch (Throwable ex) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                "Post-processing of merged bean definition failed", ex);
            }
            mbd.postProcessed = true;
        }
    }

    // Eagerly cache singletons to be able to resolve circular references
    // even when triggered by lifecycle interfaces like BeanFactoryAware.
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
                                      isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        if (logger.isDebugEnabled()) {
            logger.debug("Eagerly caching bean '" + beanName +
                         "' to allow for resolving potential circular references");
        }
        // this.singletonFactories.put(beanName, singletonFactory);
        // this.earlySingletonObjects.remove(beanName);
        // this.registeredSingletons.add(beanName);
		// 这个主要方法完成了上面3行代码
        // 并且通过一个匿名类执行了getEarlyBeanReference方法
        // 这个方法调用了实现了SmartInstantiationAwareBeanPostProcessor接口的getEarlyBeanReference方法
        // AOP动态代理就是在这边完成的，这边会将目标bean生成一个代理对象放入singletonFactories
        // 最后spring会将代理对象作为bean实例放入SingletonObjects中
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // Initialize the bean instance.
    Object exposedObject = bean;
    try {
        // 这边注入bean的属性，主要通过调用下面两个后置处理器的postProcessPropertyValues方法实现
        // AutowiredAnnotationBeanPostProcessor，CommonAnnotationBeanPostProcessor
        // 这边注入bean最终还是会调用getBean方法，进行循环依赖在第一个getSingleton方法有解释
        populateBean(beanName, mbd, instanceWrapper);
        // 这个方法调用了所有后置处理器声明周期方法
        // 如果bean实现了BeanNameAware、BeanClassLoaderAware、BeanFactoryAware接口
        // 则调用其对应的set方法
        // 如果bean实现InitializingBean接口则调用afterPropertiesSet方法
        // 依次顺序：
        // 1、BeanNameAware的setBeanName方法
        // 2、BeanClassLoaderAware的setBeanClassLoader方法
        // 3、BeanFactoryAware的setBeanFactory方法
        // 4、所有后置处理器的postProcessBeforeInitialization方法
        // 5、InitializingBean的afterPropertiesSet方法
        // 6、所有后置处理器的postProcessAfterInitialization方法
        // 到这边就完成了一个bean的初始化过程
        exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    catch (Throwable ex) {
        if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
            throw (BeanCreationException) ex;
        }
        else {
            throw new BeanCreationException(
                mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
        }
    }

    if (earlySingletonExposure) {
        // 这边又从第一个getSingleton方法取出bean
        // 因为bean在实例化后可能会进行动态代理，需要将代理对象返回出去
        // 所以又从这个方法从earlySingletonObjects中取出最终完成初始化的bean
        Object earlySingletonReference = getSingleton(beanName, false);
        if (earlySingletonReference != null) {
            if (exposedObject == bean) {
                exposedObject = earlySingletonReference;
            }
            else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                for (String dependentBean : dependentBeans) {
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        actualDependentBeans.add(dependentBean);
                    }
                }
                if (!actualDependentBeans.isEmpty()) {
                    throw new BeanCurrentlyInCreationException(beanName,
                                                               "Bean with name '" + beanName + "' has been injected into other beans [" +
                                                               StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                                                               "] in its raw version as part of a circular reference, but has eventually been " +
                                                               "wrapped. This means that said other beans do not use the final version of the " +
                                                               "bean. This is often the result of over-eager type matching - consider using " +
                                                               "'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
                }
            }
        }
    }

    // Register bean as disposable.
    try {
        registerDisposableBeanIfNecessary(beanName, bean, mbd);
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanCreationException(
            mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
    }

    return exposedObject;
}
```



## 重点类

### ConfigurationClassPostProcessor

ConfigurationClassPostProcessor实现了BeanDefinitionRegistryPostProcessor接口，分别实现了postProcessBeanDefinitionRegistry和postProcessBeanFactory这两个方法，接下来这两个方法具体做了什么。

#### postProcessBeanDefinitionRegistry

这个方法完成了对所有加了注解类的扫描

这个方法主要进入下面这个方法看

```java
processConfigBeanDefinitions(registry);
```

```java
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    // 定义一个BDHolder list，用来存放符合条件的BDHolder
    List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
    // 这边取出所有的beanName，这边应该是7个，6个是spring内部的，1个是自定义的配置类AppConfig
    String[] candidateNames = registry.getBeanDefinitionNames();

    for (String beanName : candidateNames) {
        BeanDefinition beanDef = registry.getBeanDefinition(beanName);
        // 这边有Full和Lite两个标识，具体作用后面再谈。这边只是判断bd有没有这两个标识，
        // 如果有表示这个bd已经被处理过了，直接跳过
        if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
            ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
            }
        }
        // 这边主要判断bdCLass是不是注解类，是的话放到list中，主要看下方法里下面这段代码
        /*if (isFullConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
		}
		else if (isLiteConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
		}
		else {
			return false;
		}
         */
        // 如果有@Configuration注解，则将其标志位Full
        // 如果有@Component、ComponentScan、Import、ImportResource、Bean注解标志位Lite
        // 其它则返回false
        else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
            configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
        }
    }

    // Return immediately if no @Configuration classes were found
    // 如果list空直接返回
    if (configCandidates.isEmpty()) {
        return;
    }

    // Sort by previously determined @Order value, if applicable
    // 这一步是进行排序，不重要
    configCandidates.sort((bd1, bd2) -> {
        int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
        int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
        return Integer.compare(i1, i2);
    });

    // Detect any custom bean name generation strategy supplied through the enclosing application context
    // 这边是beanName生成器不是很重要
    SingletonBeanRegistry sbr = null;
    if (registry instanceof SingletonBeanRegistry) {
        sbr = (SingletonBeanRegistry) registry;
        if (!this.localBeanNameGeneratorSet) {
            BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
            if (generator != null) {
                this.componentScanBeanNameGenerator = generator;
                this.importBeanNameGenerator = generator;
            }
        }
    }

    if (this.environment == null) {
        this.environment = new StandardEnvironment();
    }

    // Parse each @Configuration class
    // 这边定义一个配置类解析器
    ConfigurationClassParser parser = new ConfigurationClassParser(
        this.metadataReaderFactory, this.problemReporter, this.environment,
        this.resourceLoader, this.componentScanBeanNameGenerator, registry);

    // 这边用一个Set接收去重
    Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
    // 定义一个用来存放已经解析过的Set
    Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
    do {
        // 这个方法是核心，进行解析
        // 这个方法内部会递归解析出所有注解@Component、@Import、@ComponentScan等注解
        // 最后会将解析出来的类放入到ConfigurationClasses Map中
        parser.parse(candidates);
        parser.validate();

        // 用一个Set取出configurationClasses这个Map的所有key
        Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
        configClasses.removeAll(alreadyParsed);

        // Read the model and create bean definitions based on its content
        if (this.reader == null) {
            this.reader = new ConfigurationClassBeanDefinitionReader(
                registry, this.sourceExtractor, this.resourceLoader, this.environment,
                this.importBeanNameGenerator, parser.getImportRegistry());
        }
        // 加载所有的bd，这边主要是加载被Import进来、加了@bean注解的bd、xml配置、和实现ImportBeanDefinitionRegistrar接口手动注册进来的bd
        // 其它@Component的bd在扫描的时候已经直接放到bdMap中了
        this.reader.loadBeanDefinitions(configClasses);
        alreadyParsed.addAll(configClasses);

        candidates.clear();
        if (registry.getBeanDefinitionCount() > candidateNames.length) {
            String[] newCandidateNames = registry.getBeanDefinitionNames();
            Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
            Set<String> alreadyParsedClasses = new HashSet<>();
            for (ConfigurationClass configurationClass : alreadyParsed) {
                alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
            }
            for (String candidateName : newCandidateNames) {
                if (!oldCandidateNames.contains(candidateName)) {
                    BeanDefinition bd = registry.getBeanDefinition(candidateName);
                    if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
                        !alreadyParsedClasses.contains(bd.getBeanClassName())) {
                        candidates.add(new BeanDefinitionHolder(bd, candidateName));
                    }
                }
            }
            candidateNames = newCandidateNames;
        }
    }
    while (!candidates.isEmpty());

    // Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
    if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
        sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
    }

    if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
        // Clear cache in externally provided MetadataReaderFactory; this is a no-op
        // for a shared cache since it'll be cleared by the ApplicationContext.
        ((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
    }
}
```

##### parse

parse方法内部会循环传入的bdHolder Set，然后判断bd的类型调用不同的parse重载方法，这边我们传进来的AppConfigBD是AnnotatedBeanDefinition类型，所以进入下面这个方法

```java
protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
	processConfigurationClass(new ConfigurationClass(metadata, beanName));
}
```

进入processConfigurationClass方法

```java
protected void processConfigurationClass(ConfigurationClass configClass) throws IOException {
    // 这边做个判断是否需要跳过
    if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
        return;
    }

    ConfigurationClass existingClass = this.configurationClasses.get(configClass);
    // 判断当前configClass是否已经在configurationClasses map中
    if (existingClass != null) {
        // 如果新进来的配置类被加了Imported标识，直接返回
        if (configClass.isImported()) {
            if (existingClass.isImported()) {
                existingClass.mergeImportedBy(configClass);
            }
            // Otherwise ignore new imported config class; existing non-imported class overrides it.
            return;
        }
        else {
            // Explicit bean definition found, probably replacing an import.
            // Let's remove the old one and go with the new one.
            // 如果已经存在，则将老的删掉，执行下面代码放入新的configClass
            this.configurationClasses.remove(configClass);
            this.knownSuperclasses.values().removeIf(configClass::equals);
        }
    }

    // Recursively process the configuration class and its superclass hierarchy.
    SourceClass sourceClass = asSourceClass(configClass);
    do {
        // 
        sourceClass = doProcessConfigurationClass(configClass, sourceClass);
    }
    while (sourceClass != null);

    // 将返回的sourceClass放入configurationClasses中，这个Map在跳出parse方法后会处理
    // 方法内部不管是扫描还是import进来的类，都会递归执行processConfigurationClass这个方法，所以最终都会走到这一步，将sourceClass放入到map中，解析完成后统一再处理
    this.configurationClasses.put(configClass, configClass);
}
```

进入doProcessConfigurationClass方法

```java
protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass)
			throws IOException {

    // Recursively process any member (nested) classes first
    // 这边是处理内部类，就不看了一般用不到
    processMemberClasses(configClass, sourceClass);

    // Process any @PropertySource annotations
    // 这边解析所有@PropertySource注解
    for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
        sourceClass.getMetadata(), PropertySources.class,
        org.springframework.context.annotation.PropertySource.class)) {
        if (this.environment instanceof ConfigurableEnvironment) {
            processPropertySource(propertySource);
        }
        else {
            logger.warn("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
                        "]. Reason: Environment must implement ConfigurableEnvironment");
        }
    }

    // Process any @ComponentScan annotations
    // 解析@ComponentScan注解
    Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
        sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
    if (!componentScans.isEmpty() &&
        !this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
        for (AnnotationAttributes componentScan : componentScans) {
            // The config class is annotated with @ComponentScan -> perform the scan immediately
            // 这一步会扫描包，将加了@Component注解的类扫描出来转换成bd并放入到bdMap中
            // 这边扫描包spring用了asm技术，其它代码就是将类register到bdMap中
            Set<BeanDefinitionHolder> scannedBeanDefinitions =
                this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
            // Check the set of scanned definitions for any further config classes and parse recursively if needed
            for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
                BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
                if (bdCand == null) {
                    bdCand = holder.getBeanDefinition();
                }
                // 这一步是判断如果扫描出来的类也是注解类，则进行递归执行parse方法
                if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                    parse(bdCand.getBeanClassName(), holder.getBeanName());
                }
            }
        }
    }

    // Process any @Import annotations
    // 这个方法是处理所有@Import注解
    // 通过getImports(sourceClass)方法，取出被Improt的类，如果没有则会在进入方法后判断跳出
    // 这个方法接下来会具体分析
    processImports(configClass, sourceClass, getImports(sourceClass), true);

    // Process any @ImportResource annotations
    AnnotationAttributes importResource =
        AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
    if (importResource != null) {
        String[] resources = importResource.getStringArray("locations");
        Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
        for (String resource : resources) {
            String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
            configClass.addImportedResource(resolvedResource, readerClass);
        }
    }

    // Process individual @Bean methods
    Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
    for (MethodMetadata methodMetadata : beanMethods) {
        configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
    }

    // Process default methods on interfaces
    processInterfaces(configClass, sourceClass);

    // Process superclass, if any
    if (sourceClass.getMetadata().hasSuperClass()) {
        String superclass = sourceClass.getMetadata().getSuperClassName();
        if (superclass != null && !superclass.startsWith("java") &&
            !this.knownSuperclasses.containsKey(superclass)) {
            this.knownSuperclasses.put(superclass, configClass);
            // Superclass found, return its annotation metadata and recurse
            return sourceClass.getSuperClass();
        }
    }

    // No superclass -> processing is complete
    return null;
}
```

###### processImports

spring@Import注解中传入的类分三种类型：

- 实现了ImportSelector接口，其实现方法selectImports用来返回一些类的全路径的字符串数组
- 实现了ImportBeanDefinitionRegistrar接口，其实现方法registerBeanDefinitions中传入了(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry)可以供开发者进行扩展
- 普通类

```java
private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, boolean checkForCircularImports) {

    // 如果没有被Import的类直接返回
    // importCandidates是先通过外面获取@Import注解中引入的类，然后再传入到方法中进行判断是否为空
    // 而不是直接在方法中将@Import元数据传进来，进行判断
    // 这是因为这个方法是个递归方法
    if (importCandidates.isEmpty()) {
        return;
    }

    if (checkForCircularImports && isChainedImportOnStack(configClass)) {
        this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
    }
    else {
        this.importStack.push(configClass);
        try {
            // 循环被Import的类
            for (SourceClass candidate : importCandidates) {
                // 实现了ImportSelector接口
                if (candidate.isAssignable(ImportSelector.class)) {
                    // Candidate class is an ImportSelector -> delegate to it to determine imports
                    Class<?> candidateClass = candidate.loadClass();
                    // 实例化实现了ImportSelector接口的类
                    ImportSelector selector = BeanUtils.instantiateClass(candidateClass, ImportSelector.class);
                    // 如果被Import进来的类实现了一些Aware接口，则在这个方法内执行这些Aware的是实现方法
                    ParserStrategyUtils.invokeAwareMethods(
                        selector, this.environment, this.resourceLoader, this.registry);
                    // 如果是DeferredImportSelector类型的，那么放到deferredImportSelectors中
                    // DeferredImportSelector是一个延迟ImportSelector，后面会对这个List统一处理
                    if (this.deferredImportSelectors != null && selector instanceof DeferredImportSelector) {
                        this.deferredImportSelectors.add(
                            new DeferredImportSelectorHolder(configClass, (DeferredImportSelector) selector));
                    }
                    else {
                        // 执行实现方法selectImports，得到返回的类的全路径（如com.TestService）
                        String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
                        // 根据类的路径，解析这些类作为SourceClass
                        Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames);
                        // 这边将得到的类又调用processImports方法进行递归，防止通过这个方法得到的类也加了@Import注解
                        // 这些类再次进入这个方法，如果同样实现了ImportSelector接口，则会又走到这一步进行递归，如果不是则会进入接下来的判断。所以ImportSelector接口的作用其实类似于@Import，他们的最终结果是都是将Import进来的类走接下来的判断
                        processImports(configClass, currentSourceClass, importSourceClasses, false);
                    }
                }
                // 实现了ImportBeanDefinitionRegistrar接口
                else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                    // Candidate class is an ImportBeanDefinitionRegistrar ->
                    // delegate to it to register additional bean definitions
                    Class<?> candidateClass = candidate.loadClass();
                    // 实例化实现了ImportBeanDefinitionRegistrar接口的类
                    ImportBeanDefinitionRegistrar registrar =
                        BeanUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class);
                    // 这边同上一个判断中的这一步功能一样
                    ParserStrategyUtils.invokeAwareMethods(
                        registrar, this.environment, this.resourceLoader, this.registry);
                    // 将实例放入importBeanDefinitionRegistrars Map中，后面会具体处理
                    configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                }
                else {
                    // Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
                    // process it as an @Configuration class
                    this.importStack.registerImport(
                        currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
                    // 这边是处理普通类，又递归进入processConfigurationClass方法
                    // 和@scanComponent注解一样，扫描出来的注解类又会递归执行解析
                    processConfigurationClass(candidate.asConfigClass(configClass));
                }
            }
        }
        catch (BeanDefinitionStoreException ex) {
            throw ex;
        }
        catch (Throwable ex) {
            throw new BeanDefinitionStoreException(
                "Failed to process import candidates for configuration class [" +
                configClass.getMetadata().getClassName() + "]", ex);
        }
        finally {
            this.importStack.pop();
        }
    }
}
```

##### loadBeanDefinitions

```java
private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {

    if (trackedConditionEvaluator.shouldSkip(configClass)) {
        String beanName = configClass.getBeanName();
        if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
            this.registry.removeBeanDefinition(beanName);
        }
        this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
        return;
    }

    // 将被Import进来的类注册到bdMap中
    if (configClass.isImported()) {
        registerBeanDefinitionForImportedConfigurationClass(configClass);
    }
    // 将@Bean注解的bd注册到bdMap中
    for (BeanMethod beanMethod : configClass.getBeanMethods()) {
        loadBeanDefinitionsForBeanMethod(beanMethod);
    }

    // 这一步是解析xml定义的bd
    loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
    // 如果实现了ImportBeanDefinitionRegistrar接口，则在这一步执行他的实现方法registerBeanDefinitions，可以在这个方法内实现注册一个bd到bdMap中
    /*
    BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazzz);
                                GenericBeanDefinition beanDefinition = (GenericBeanDefinition) beanDefinitionBuilder.getBeanDefinition();
                                String className = beanDefinition.getBeanClassName();
                                beanDefinition.setBeanClass(MyFactoryBean.class);
                                beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(className);
                                registry.registerBeanDefinition(beanName, beanDefinition);
    */
    loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
}
```



#### postProcessBeanFactory

这个方法主要是将加了@Configuration注解的类进行Cglib代理，对于加了@Bean注解的方法进行代理，如果bean作用域是singleton的，通过代理这些方法只有在第一次调用父类的创建bean实例方法，之后调用都会根据beanName直接从beanFactory中取出bean，以保证bean是单例的

```java
public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    int factoryId = System.identityHashCode(beanFactory);
    if (this.factoriesPostProcessed.contains(factoryId)) {
        throw new IllegalStateException(
            "postProcessBeanFactory already called on this post-processor against " + beanFactory);
    }
    this.factoriesPostProcessed.add(factoryId);
    // 这边判断如果还没执行processConfigBeanDefinitions这一步，则在这边执行一下
    if (!this.registriesPostProcessed.contains(factoryId)) {
        // BeanDefinitionRegistryPostProcessor hook apparently not supported...
        // Simply call processConfigurationClasses lazily at this point then.
        processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
    }

    // 这个方法内部会轮询所有bd，判断BD是否被标识为Full（这个标识在processConfigBeanDefinitions方法中进行标识，加了@Configuration注解的类会标识为Full），将所有标识Full的类进行Cglib代理
    // 具体看enhanceConfigurationClasses方法代码注释
    enhanceConfigurationClasses(beanFactory);
    beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
}
```

##### enhanceConfigurationClasses

```java
public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
    // 定义一个map用来存放加了@Configuration注解的bd
    Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
    // 循环beanFactory中所有bd，如果是Full标识即@Configuration注解的放入map
    for (String beanName : beanFactory.getBeanDefinitionNames()) {
        BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
        if (ConfigurationClassUtils.isFullConfigurationClass(beanDef)) {
            if (!(beanDef instanceof AbstractBeanDefinition)) {
                throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
                                                       beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
            }
            else if (logger.isWarnEnabled() && beanFactory.containsSingleton(beanName)) {
                logger.warn("Cannot enhance @Configuration bean definition '" + beanName +
                            "' since its singleton instance has been created too early. The typical cause " +
                            "is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
                            "return type: Consider declaring such methods as 'static'.");
            }
            configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
        }
    }
    // 没有直接返回
    if (configBeanDefs.isEmpty()) {
        // nothing to enhance -> return immediately
        return;
    }

    // 工具类，用来完成Cglib代理
    ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
    for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
        AbstractBeanDefinition beanDef = entry.getValue();
        // If a @Configuration class gets proxied, always proxy the target class
        beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
        try {
            // Set enhanced subclass of the user-specified bean class
            // 先获得原来的配置类，作为父类
            Class<?> configClass = beanDef.resolveBeanClass(this.beanClassLoader);
            if (configClass != null) {
                // 执行代理，返回一个Cglib代理的Class
                // 这个方法实际上就是new了一个Enhancer，具体看下面代码
                Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
                if (configClass != enhancedClass) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(String.format("Replacing bean definition '%s' existing class '%s' with " +
                                                   "enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
                    }
                    // 将代理Class作为原来bd的BeanClass
                    beanDef.setBeanClass(enhancedClass);
                }
            }
        }
        catch (Throwable ex) {
            throw new IllegalStateException("Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
        }
    }
}
```

创建一个新的Cglib代理实对象

```java
private Enhancer newEnhancer(Class<?> configSuperClass, @Nullable ClassLoader classLoader) {
    Enhancer enhancer = new Enhancer();
    // 将目标对象设为父类继承他
    enhancer.setSuperclass(configSuperClass);
    // 实现EnhancedConfiguration接口，这个接口继承了BeanFactoryAware接口，实际上就是提供了一个setBeanFactory方法，因为对配置类代理过后，加了@Bean注解的方法只有在第一次实例化对象的时候调父类的方法，之后再去调这个方法的时候代理对象直接会根据beanName从beanFactory中去拿，所以需要一个beanFactory
    enhancer.setInterfaces(new Class<?>[] {EnhancedConfiguration.class});
    enhancer.setUseFactory(false);
    enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
    // 这里面传一个生成类的策略，这边主要声明了一个BeanFactory变量用$$beanFactory表示
    enhancer.setStrategy(new BeanFactoryAwareGeneratorStrategy(classLoader));
    // 这边是传入代理的Callback，用来对父类方法进行拦截代理
    // Spring这边主要用了BeanMethodInterceptor、BeanFactoryAwareMethodInterceptor这两个Callback
    // 这边传入的Callback都会实现MethodInterceptor接口的intercept方法，解析来看这两个Callback的具体作用分析
    enhancer.setCallbackFilter(CALLBACK_FILTER);
    enhancer.setCallbackTypes(CALLBACK_FILTER.getCallbackTypes());
    return enhancer;
}
```

###### BeanMethodInterceptor

这个类实现了MethodInterceptor, ConditionalCallback这两个接口，其中MethodInterceptor接口实现了intercept方法对目标父类的方法进行拦截代理，ConditionalCallback实现了其isMatch方法用来匹配只有加了@Bean注解的方法；接下来主要分析intercept方法。

```java
public Object intercept(Object enhancedConfigInstance, Method beanMethod, Object[] beanMethodArgs,
					MethodProxy cglibMethodProxy) throws Throwable {

    // 获取beanFactory
    ConfigurableBeanFactory beanFactory = getBeanFactory(enhancedConfigInstance);
    // 根据beanMethod获取beanName
    String beanName = BeanAnnotationHelper.determineBeanNameFor(beanMethod);

    // Determine whether this bean is a scoped-proxy
    Scope scope = AnnotatedElementUtils.findMergedAnnotation(beanMethod, Scope.class);
    if (scope != null && scope.proxyMode() != ScopedProxyMode.NO) {
        String scopedBeanName = ScopedProxyCreator.getTargetBeanName(beanName);
        if (beanFactory.isCurrentlyInCreation(scopedBeanName)) {
            beanName = scopedBeanName;
        }
    }

    // To handle the case of an inter-bean method reference, we must explicitly check the
    // container for already cached instances.

    // First, check to see if the requested bean is a FactoryBean. If so, create a subclass
    // proxy that intercepts calls to getObject() and returns any cached bean instance.
    // This ensures that the semantics of calling a FactoryBean from within @Bean methods
    // is the same as that of referring to a FactoryBean within XML. See SPR-6602.
    // 这边是有关factoryBean的处理，先放一下
    if (factoryContainsBean(beanFactory, BeanFactory.FACTORY_BEAN_PREFIX + beanName) &&
        factoryContainsBean(beanFactory, beanName)) {
        Object factoryBean = beanFactory.getBean(BeanFactory.FACTORY_BEAN_PREFIX + beanName);
        if (factoryBean instanceof ScopedProxyFactoryBean) {
            // Scoped proxy factory beans are a special case and should not be further proxied
        }
        else {
            // It is a candidate FactoryBean - go ahead with enhancement
            return enhanceFactoryBean(factoryBean, beanMethod.getReturnType(), beanFactory, beanName);
        }
    }

    // 如果第一次调用@bean方法，则会进入这个判断，调用父类原始方法创建bean实例
    if (isCurrentlyInvokedFactoryMethod(beanMethod)) {
        // The factory is calling the bean method in order to instantiate and register the bean
        // (i.e. via a getBean() call) -> invoke the super implementation of the method to actually
        // create the bean instance.
        if (logger.isWarnEnabled() &&
            BeanFactoryPostProcessor.class.isAssignableFrom(beanMethod.getReturnType())) {
            logger.warn(String.format("@Bean method %s.%s is non-static and returns an object " +
                                      "assignable to Spring's BeanFactoryPostProcessor interface. This will " +
                                      "result in a failure to process annotations such as @Autowired, " +
                                      "@Resource and @PostConstruct within the method's declaring " +
                                      "@Configuration class. Add the 'static' modifier to this method to avoid " +
                                      "these container lifecycle issues; see @Bean javadoc for complete details.",
                                      beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName()));
        }
        return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
    }

    // 如果不是第一次调用，则会进入这个方法，根据beanName从factoryBean中取出bean
    return resolveBeanReference(beanMethod, beanMethodArgs, beanFactory, beanName);
}
```



###### BeanFactoryAwareMethodInterceptor

BeanFactoryAwareMethodInterceptor接口和BeanMethodInterceptor同样实现了MethodInterceptor, ConditionalCallback这两个接口，这个类主要是拦截setBeanFatory方法，给$$beanFactory属性设置

```java
private static class BeanFactoryAwareMethodInterceptor implements MethodInterceptor, ConditionalCallback {

    // 这个方法实际上就是对setBeanFactory的实现
    @Override
    @Nullable
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // 取出$$beanFactory属性
        Field field = ReflectionUtils.findField(obj.getClass(), BEAN_FACTORY_FIELD);
        Assert.state(field != null, "Unable to find generated BeanFactory field");
        // 给属性赋值
        field.set(obj, args[0]);

        // Does the actual (non-CGLIB) superclass implement BeanFactoryAware?
        // If so, call its setBeanFactory() method. If not, just exit.
        // 如果cglib代理类的父类本身对BeanFactoryAware完成了实现，则调用其父类的实现方法
        if (BeanFactoryAware.class.isAssignableFrom(ClassUtils.getUserClass(obj.getClass().getSuperclass()))) {
            return proxy.invokeSuper(obj, args);
        }
        return null;
    }

    // 这个方法用来判断是否是setBeanFactory方法
    @Override
    public boolean isMatch(Method candidateMethod) {
        return (candidateMethod.getName().equals("setBeanFactory") &&
                candidateMethod.getParameterCount() == 1 &&
                BeanFactory.class == candidateMethod.getParameterTypes()[0] &&
                BeanFactoryAware.class.isAssignableFrom(candidateMethod.getDeclaringClass()));
    }
}
```

