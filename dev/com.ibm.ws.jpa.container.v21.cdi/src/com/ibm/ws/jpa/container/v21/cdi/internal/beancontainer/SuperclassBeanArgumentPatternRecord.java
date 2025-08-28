package com.ibm.ws.jpa.container.v21.cdi.internal.beancontainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SuperclassBeanArgumentPatternRecord {

    public final Optional<String> name;
    public final Class beanType;
    public final Object beanLifecycleOptions; //This is different from subclass
    public final Object fallbackProducer;

    @SuppressWarnings("rawtypes")
    public SuperclassBeanArgumentPatternRecord(Object[] args) {
        if (args.length == 4) {
            String name = (String) args[0];
            this.name = Optional.of(name);
            Class clazz = (Class) args[1];
            beanType = clazz;
            beanLifecycleOptions = args[2];
            fallbackProducer = args[3];
        } else {
            name = Optional.empty();
            Class clazz = (Class) args[0];
            beanType = clazz;
            beanLifecycleOptions = args[1];
            fallbackProducer = args[2];
        }

    }

    /*
     * Gets the arguments types to match these methods:
     * public interface BeanLifecycleStrategy {
     * <B> ContainedBeanImplementor<B> createBean(
     * Class<B> beanClass,
     * BeanInstanceProducer fallbackProducer,
     * BeanContainer beanContainer);
     *
     * <B> ContainedBeanImplementor<B> createBean(
     * String beanName,
     * Class<B> beanClass,
     * BeanInstanceProducer fallbackProducer,
     * BeanContainer beanContainer);
     * }
     */

    @SuppressWarnings("rawtypes")
    public Class[] getArgumentParamatersForLifestyleStrategyDotCreateBean() {

        List<Class<?>> arguments = new ArrayList<Class<?>>();

        name.map(Object::getClass).ifPresent(arguments::add);
        arguments.add(Class.class); //Class<B> beanType
        arguments.add(HibernateClassFinder.getBeanLifecycleOptionsInterface());
        arguments.add(HibernateClassFinder.getBeanContainerInterface());

        return arguments.toArray(new Class[0]);
    }

    @SuppressWarnings("rawtypes")
    public Class[] getArgumentsForLifestyleStrategyDotCreateBean() {

        List<Object> arguments = new ArrayList<Object>();

        name.ifPresent(arguments::add);
        arguments.add(beanType); //Class<B> beanType
        arguments.add(beanLifecycleOptions);
        arguments.add(fallbackProducer);

        return arguments.toArray(new Class[0]);
    }
}
