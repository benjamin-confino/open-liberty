package com.ibm.ws.jpa.container.v21.cdi.internal.beancontainer;

import java.lang.reflect.Proxy;

public class HibernateClassFinder {

    private static Class<?> originalHibernateClass;
    public static Class<?> getOriginalHibernameClass() {
        if (originalHibernateClass == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                originalHibernateClass = Class.forName("CdiBeanContainerExtendedAccessImpl", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return originalHibernateClass;
    }

    private static Class<?> containedBeanImplementorInterface;
    public static Class<?> getContainedBeanImplementorInterface() {
        if (containedBeanImplementorInterface == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                containedBeanImplementorInterface = Class.forName("ContainedBeanImplementor", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return containedBeanImplementorInterface;
    }

    private static Class<?> cdiBasedBeanContainerInterface;
    public static Class<?> getCdiBasedBeanContainerInterface() {
        if (containedBeanImplementorInterface == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                cdiBasedBeanContainerInterface = Class.forName("CdiBasedBeanContainer", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return cdiBasedBeanContainerInterface;
    }
    
    private static Class<?> beanContainerInterface;
    public static Class<?> getBeanContainerInterface() {
        if (beanContainerInterface == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                beanContainerInterface = Class.forName("BeanContainer", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return beanContainerInterface;
    }
    
    private static Object dummyBeanContainer;
    public static Object getDummyBeanContainer() {
        if (dummyBeanContainer == null) {
            DummyBeanContainer dbc = new DummyBeanContainer();
            Class<?> cdiBasedBeanContainerInterface = getCdiBasedBeanContainerInterface();
            dummyBeanContainer = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { cdiBasedBeanContainerInterface }, dbc);
        }

        return dummyBeanContainer;
    }
    
    private static Class<?> beanLifecycleStrategyInterface;
    public static Class<?> getBeanLifecycleStrategyInterface() {
        if (beanLifecycleStrategyInterface == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                beanLifecycleStrategyInterface = Class.forName("org.hibernate.resource.beans.container.spi.BeanLifecycleStrategy", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return beanLifecycleStrategyInterface;
    }
    
    private static Class<?> beanLifecycleOptionsInterface;
    public static Class<?> getBeanLifecycleOptionsInterface() {
        if (beanLifecycleOptionsInterface == null) {

            //Since this method should only be invoked by Hibernate the TCCL should be the appCL
            ClassLoader appCL = Thread.currentThread().getContextClassLoader();
            try {
                beanLifecycleOptionsInterface = Class.forName("org.hibernate.resource.beans.container.spi.BeanContainer$LifecycleOptions", false, appCL);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return beanLifecycleOptionsInterface;
    }

}
