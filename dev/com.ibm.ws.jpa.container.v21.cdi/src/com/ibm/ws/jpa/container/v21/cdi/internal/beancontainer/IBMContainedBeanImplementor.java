//This class is a copy of both the embedded inner classes from
// https://github.com/hibernate/hibernate-orm/blob/7.1.0/hibernate-core/src/main/java/org/hibernate/resource/beans/container/internal/CdiBeanContainerExtendedAccessImpl.java

//Copyright belongs to RedHat, used here under the terms of apache 2.0.

package com.ibm.ws.jpa.container.v21.cdi.internal.beancontainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import com.ibm.websphere.ras.annotation.Sensitive;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;

public class IBMContainedBeanImplementor<B> implements InvocationHandler {

    private final Optional<String> name;
    private final Class<B> beanType;
    private final Object lifecycleStrategy; //Actual type BeanLifecycleStrategy
    private final Object fallbackProducer; //Actual type BeanInstanceProducer

    private final Class[] argumentParamatersForLifecylceStrategy;
    private final Object[] argumentsForLifecylceStrategy;

    private Object delegateContainedBean;

    @SuppressWarnings("unchecked")
    public IBMContainedBeanImplementor(SubclassBeanArgumentPatternRecord args) {
        //this(args.name, args.beanType, args.beanLifecycleStrategy, args.fallbackProducer);

        this.name = args.name;
        this.beanType = args.beanType;
        this.lifecycleStrategy = args.beanLifecycleStrategy;
        this.fallbackProducer = args.fallbackProducer;
        this.argumentParamatersForLifecylceStrategy = args.getArgumentParamatersForLifestyleStrategyDotCreateBean();
        this.argumentsForLifecylceStrategy = args.getArgumentsForLifestyleStrategyDotCreateBean();
    }

    @Override
    @FFDCIgnore(InvocationTargetException.class)
    // proxy is @Sensitive to avoid infinite recursion
    // args is @Sensitive to avoid tracing parameters we don't care about
    public Object invoke(@Sensitive Object proxy, Method method, @Sensitive Object[] args) throws Throwable {
        Object toReturn;

        //Handles https://github.com/hibernate/hibernate-orm/blob/7.1.0/hibernate-core/src/main/java/org/hibernate/resource/beans/container/spi/ContainedBeanImplementor.java
        switch (method.getName()) {
            case "initialize":
                initialize();
                break;
            case "release":
                release();
                break;
        }

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Methods from upstream begin here
    ////////////////////////////////////////////////////////////////////////////

    public Class<B> getBeanClass() {
        return beanType;
    }

    /*
     * if ( delegateContainedBean == null ) {
     * delegateContainedBean = lifecycleStrategy.createBean(
     * name,
     * beanType,
     * fallbackProducer,
     * DUMMY_BEAN_CONTAINER
     * );
     * delegateContainedBean.initialize();
     * }
     */
    public void initialize() {

        if (delegateContainedBean == null) {
            try {
                Method m = HibernateClassFinder.getBeanLifecycleStrategyInterface().getMethod("createBean", argumentParamatersForLifecylceStrategy);
                delegateContainedBean = m.invoke(m, argumentsForLifecylceStrategy);

            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public Object getBeanInstance() {
        if (delegateContainedBean == null) {
            initialize();
        }
        try {
            Method m = delegateContainedBean.getClass().getMethod("getBeanInstance", null);
            Object beanInstance = m.invoke(m, null);
            return beanInstance;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null; //TODO better error
    }

    public void release() {

        try {
            Method m = delegateContainedBean.getClass().getMethod("release", null);
            m.invoke(m, null);

        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        delegateContainedBean = null;
    }

}
