package com.ibm.ws.jpa.container.v21.cdi.internal.beancontainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.ibm.websphere.ras.annotation.Sensitive;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;

//Implements the DummyBeanContainer from https://github.com/hibernate/hibernate-orm/blob/7.1.0/hibernate-core/src/main/java/org/hibernate/resource/beans/container/internal/CdiBeanContainerExtendedAccessImpl.java#L35
public class DummyBeanContainer implements InvocationHandler {

    @Override
    @FFDCIgnore(InvocationTargetException.class)
    // proxy is @Sensitive to avoid infinite recursion
    // args is @Sensitive to avoid tracing parameters we don't care about
    public Object invoke(@Sensitive Object proxy, Method method, @Sensitive Object[] args) throws Throwable {

        if (method.getName().equals("stop")) {
            //do nothing
        } else {
            throw new UnsupportedOperationException();
        }
        return null;
    }
}
