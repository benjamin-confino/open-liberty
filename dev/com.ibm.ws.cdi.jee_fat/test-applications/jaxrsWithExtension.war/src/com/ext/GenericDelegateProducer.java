package com.ext;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Generic Delegate Producer class.
 */
public class GenericDelegateProducer
    
{
    @Produces
    @Dependent
    @GenericDelegateQualifier
    public Object produce(InjectionPoint injectionPoint, BeanManager beanManager)
    {
        //GenericDelegateQualifier genericDelegateAnnotation = BeanUtils.extractAnnotation(injectionPoint.getAnnotated(), GenericDelegateQualifier.class);

        return new MyInterface() {
			@Override
			public String proceed() {
				return "This is ok"; //here the concrete implementation
			}
		};
    }
}
