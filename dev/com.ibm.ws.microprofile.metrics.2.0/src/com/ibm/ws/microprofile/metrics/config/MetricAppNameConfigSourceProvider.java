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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

import com.ibm.ws.microprofile.metrics.MetricsConfig;

/**
 *
 */
@Component(service = { MetricAppNameConfigSourceProvider.class }, configurationPid = "com.ibm.ws.microprofile.metrics", configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true, property = { "service.vendor=IBM" })
public class MetricAppNameConfigSourceProvider implements org.eclipse.microprofile.config.spi.ConfigSourceProvider {

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader arg0) {
        List<ConfigSource> configSources = new ArrayList<>();
        ConfigSource cs = new MetricAppNameConfigSource();
        configSources.add(cs);
        return configSources;
    }

}
