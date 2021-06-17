package com.ext;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/cdiext")
public class JaxrsApplication extends Application {

    @Override
    public Set<Class<?>> getClasses()
    {
        Set<Class<?>> restClass = new HashSet<>();

        restClass.add(SimpleRestApi.class);

        return restClass;
    }

}

