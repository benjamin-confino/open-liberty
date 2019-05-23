/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.metrics.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.ibm.ws.microprofile.metrics.ApplicationListener;
import com.ibm.ws.threadContext.ComponentMetaDataAccessorImpl;

@Component(service = { MetricAppNameConfigSource.class }, configurationPid = "com.ibm.ws.microprofile.metrics.config", configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true, property = { "service.vendor=IBM" })
public class MetricAppNameConfigSource implements ConfigSource {

    private static final String METRICS_APPNAME_CONFIG_KEY = "mp.metrics.appName";

    private static final int CONFIG_ORDINAL = 80;

    private static Map<String, String> applicationContextRootMap = new HashMap<String, String>();

    {
        System.out.println("Creating Config Source normal metrics 2.0");
        System.out.println(">>>>>Config Source TCCl " + Thread.currentThread().getContextClassLoader());
        System.out.println(">>>>>>Config Source CL " + getClass().getClassLoader());
    }

    @Override
    public int getOrdinal() {
        return CONFIG_ORDINAL;
    }

    @Override
    public String getName() {
        return "Metric Instrumented Application's Name";
    }

    @Override
    public Set<String> getPropertyNames() {
        return applicationContextRootMap.keySet();
    }

    @Override
    public Map<String, String> getProperties() {
        return applicationContextRootMap;
    }

    @Override
    public String getValue(String propertyName) {
        System.out.println("Config Source is being retrieved");
        if (propertyName.equals(METRICS_APPNAME_CONFIG_KEY)) {

            String appName = resolveApplicationName();
            String contextRoot = ApplicationListener.contextRoot_Map.get(appName);
            if (contextRoot != null) {
                return contextRoot;
            }
        }
        return null;
    }

    private String resolveApplicationName() {
        String appName = null;
        try {
            ComponentMetaDataAccessorImpl cmdai = ComponentMetaDataAccessorImpl.getComponentMetaDataAccessor();
            appName = cmdai.getComponentMetaData().getModuleMetaData().getName();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return appName;
    }
}
