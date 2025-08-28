//This class is a copy of both the embedded inner classes from
// https://github.com/hibernate/hibernate-orm/blob/7.1.0/hibernate-core/src/main/java/org/hibernate/resource/beans/container/internal/CdiBeanContainerExtendedAccessImpl.java

//Copyright belongs to RedHat, used here under the terms of apache 2.0.

package com.ibm.ws.jpa.container.v21.cdi.internal.beancontainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.spi.BeanManager;

import com.ibm.websphere.ras.annotation.Sensitive;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.jpa.container.v21.cdi.internal.IBMHibernateExtendedBeanManager;

public class IBMCdiBeanContainerExtendedAccessImpl<B> implements InvocationHandler {

    private BeanManager usableBeanManager = null;

    public IBMCdiBeanContainerExtendedAccessImpl(IBMHibernateExtendedBeanManager extendedBeanManager) {
        extendedBeanManager.registerLifecycleListener(this);
    }

    @Override
    @FFDCIgnore(InvocationTargetException.class)
    // proxy is @Sensitive to avoid infinite recursion
    // args is @Sensitive to avoid tracing parameters we don't care about
    public Object invoke(@Sensitive Object proxy, Method method, @Sensitive Object[] args) throws Throwable {
        Object toReturn;

        switch (method.getName()) {
            case "createBean":
                return createBean(args);
            case "beanManagerInitialized":
                beanManagerInitialized(args);
            case "beforeBeanManagerDestroyed":
                beforeBeanManagerDestroyed(args);
            case "getUsableBeanManager":
                return getUsableBeanManager();
            case "getBeanManager":
                return getBeanManager();
        }

        return null;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Implementation of upstream methods begins here
    /////////////////////////////////////////////////////////////////////////////////////////////

    /*
     * @Override
     * protected <B> ContainedBeanImplementor<B> createBean(
     * Class<B> beanType,
     * BeanLifecycleStrategy lifecycleStrategy,
     * BeanInstanceProducer fallbackProducer) {
     * if ( usableBeanManager == null ) {
     * return new BeanImpl<>( beanType, lifecycleStrategy, fallbackProducer );
     * }
     * else {
     * return lifecycleStrategy.createBean( beanType, fallbackProducer, this );
     * }
     * }
     *
     * @Override
     * protected <B> ContainedBeanImplementor<B> createBean(
     * String name,
     * Class<B> beanType,
     * BeanLifecycleStrategy lifecycleStrategy,
     * BeanInstanceProducer fallbackProducer) {
     * if ( usableBeanManager == null ) {
     * return new NamedBeanImpl<>(
     * name,
     * beanType,
     * lifecycleStrategy,
     * fallbackProducer
     * );
     * }
     * else {
     * return lifecycleStrategy.createBean( name, beanType, fallbackProducer, this );
     * }
     * }
     */
    //Since this is all reflexive one method has to handle both arg patterns
    public Object createBean(@Sensitive Object[] args) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        SubclassBeanArgumentPatternRecord arguments = new SubclassBeanArgumentPatternRecord(args);

        Object toReturn = null;

        if (usableBeanManager == null) {
            @SuppressWarnings("rawtypes")
            IBMContainedBeanImplementor newBean = new IBMContainedBeanImplementor(arguments);
            toReturn = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { HibernateClassFinder.getContainedBeanImplementorInterface() }, newBean);
        } else {
            Object lifecycleStragey = arguments.beanLifecycleStrategy;
            Method m = lifecycleStragey.getClass().getMethod("createBean", arguments.getArgumentParamatersForLifestyleStrategyDotCreateBean());
            toReturn = m.invoke(lifecycleStragey, args);
        }

        return toReturn;
    }

    /////////////////////////////////////////////////////////////////
    /*
     * @Override
     * public void beanManagerInitialized(BeanManager beanManager) {
     * this.usableBeanManager = beanManager;
     * forEachBean( ContainedBeanImplementor::initialize );
     * }
     *
     * @Override
     * public void beforeBeanManagerDestroyed(BeanManager beanManager) {
     * stop();
     * this.usableBeanManager = null;
     * }
     *
     * @Override
     * public BeanManager getUsableBeanManager() {
     * if ( usableBeanManager == null ) {
     * throw new IllegalStateException( "ExtendedBeanManager.LifecycleListener callback not yet called: CDI not (yet) usable" );
     * }
     * return usableBeanManager;
     * }
     *
     * @Internal
     * public BeanManager getBeanManager() {
     * return usableBeanManager;
     * }
     */

    public void beanManagerInitialized(Object[] args) {

        this.usableBeanManager = (BeanManager) args[0];
        forEachBean(IBMContainedBeanImplementor::initialize);
    }

    public void beforeBeanManagerDestroyed(Object[] args) {
        stop();
        this.usableBeanManager = null;
    }

    public BeanManager getUsableBeanManager() {
        if (usableBeanManager == null) {
            throw new IllegalStateException("ExtendedBeanManager.LifecycleListener callback not yet called: CDI not (yet) usable");
        }
        return usableBeanManager;
    }

    public BeanManager getBeanManager() {
        return usableBeanManager;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //////////// These Methods come from the abstract class    ////////////////////
    // https://github.com/hibernate/hibernate-orm/blob/7.1.0/hibernate-core/src/main/java/org/hibernate/resource/beans/container/spi/AbstractCdiBeanContainer.java
    ///////////////////////////////////////////////////////////////////////////////

    private final Map<String, Object> beanCache = new HashMap<>();
    private final List<Object> registeredBeans = new ArrayList<>();

    /*
     * public <B> ContainedBean<B> getBean(
     * Class<B> beanType,
     * LifecycleOptions lifecycleOptions,
     * BeanInstanceProducer fallbackProducer) {
     * return lifecycleOptions.canUseCachedReferences()
     * ? getCacheableBean( beanType, lifecycleOptions, fallbackProducer )
     * : createBean( beanType, lifecycleOptions, fallbackProducer );
     * }
     */

    public Object getBean(Object[] args) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        SuperclassBeanArgumentPatternRecord arguments = new SuperclassBeanArgumentPatternRecord(args);

        Object lifecycleOptions = arguments.beanLifecycleOptions;
        Method m = lifecycleOptions.getClass().getMethod("canUseCachedReferences", null);
        Boolean canUseCachedReferences = (Boolean) m.invoke(lifecycleOptions, null);

        if (canUseCachedReferences) {
            getCacheableBean(args);
        }
    }

}
