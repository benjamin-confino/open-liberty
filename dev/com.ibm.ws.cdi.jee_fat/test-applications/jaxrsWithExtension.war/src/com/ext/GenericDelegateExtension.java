package com.ext;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;

/**
 * An extension that create a link between GenericDelegate annotation and producer.
 */
public class GenericDelegateExtension
    implements Extension
{
    
    /**
     * Override the field type to an Object type.
     * @param <X> Annotated type
     * @param pat Process Annotated Type
     * @param beanManager bean manager
     */
    <X> void processAnnotatedType(@Observes ProcessAnnotatedType<X> pat, BeanManager beanManager)
    {

System.out.println(pat.toString());
        AnnotatedTypeBuilder<X> builder = null;

        final AnnotatedType<X> annotatedType = pat.getAnnotatedType();

System.out.println("GREP-ACK " + pat.toString());

        for (AnnotatedField<? super X> f : annotatedType.getFields())
        {
            if (f.isAnnotationPresent(GenericDelegateQualifier.class))
            {
System.out.println("GREP-KCA " + f.toString());
                builder = initializeBuilder(builder, annotatedType); //usage of deltaspike as a facility
                builder.overrideFieldType(f, Object.class); //usage of deltaspike as a facility
            }
        }

        if (builder != null)
        {
            pat.setAnnotatedType(builder.create());
        }
    }

    /**
     * Initialize a builder.
     * @param <X> Annotated type
     * @param currentBuilder Annotated type builder
     * @param source source
     * @return the current builder or a new one from the source
     */
    private <X> AnnotatedTypeBuilder<X> initializeBuilder(final AnnotatedTypeBuilder<X> currentBuilder,
        final AnnotatedType<X> source)
    {
        if (currentBuilder == null)
        {
            return new AnnotatedTypeBuilder<X>().readFromType(source);
        }
        return currentBuilder;
    }
}
