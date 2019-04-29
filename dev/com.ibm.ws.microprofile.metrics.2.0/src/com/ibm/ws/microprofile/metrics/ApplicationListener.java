/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.metrics;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

import com.ibm.ws.container.service.app.deploy.ApplicationInfo;
import com.ibm.ws.container.service.app.deploy.EARApplicationInfo;
import com.ibm.ws.container.service.app.deploy.WebModuleInfo;
import com.ibm.ws.container.service.state.ApplicationStateListener;
import com.ibm.ws.container.service.state.StateChangeException;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.microprofile.metrics.impl.MetricRegistryImpl;
import com.ibm.ws.microprofile.metrics.impl.SharedMetricRegistries;
import com.ibm.wsspi.adaptable.module.Container;
import com.ibm.wsspi.adaptable.module.Entry;
import com.ibm.wsspi.adaptable.module.NonPersistentCache;
import com.ibm.wsspi.adaptable.module.UnableToAdaptException;

@Component(service = { ApplicationStateListener.class }, configurationPolicy = ConfigurationPolicy.IGNORE, immediate = true)
public class ApplicationListener implements ApplicationStateListener {

    private SharedMetricRegistries sharedMetricRegistry;

    public static Map<String, String> contextRoot_Map = new HashMap<String, String>();

    /** {@inheritDoc} */
    @Override
    public void applicationStarting(ApplicationInfo appInfo) throws StateChangeException {

        Container appContainer = appInfo.getContainer();

        //Goes through each container "entry" in the EAR file.
        if (appInfo instanceof EARApplicationInfo) {
            for (Entry entry : appContainer) {
                try {
                    Container subContainer = entry.adapt(Container.class);
                    if (subContainer != null) {
                        resolveContextRootFromContainer(subContainer);
                    }
                } catch (UnableToAdaptException e) {
                    e.printStackTrace();
                }
            }
        } else {
            resolveContextRootFromContainer(appContainer);
        }
    }

    /*
     * Adapts a container into a NonPersistentCache to retrieve the WebModuleInfo
     * which will subsequently provide us with the ContextRoot and ApplicationName
     * to be set in the contextRoot_Map. For use by the MetricAppNameConfigSource to configure
     * the config value with the application appropriate contextRoot
     */
    @FFDCIgnore(UnableToAdaptException.class)
    private void resolveContextRootFromContainer(Container container) {

        String contextRoot = null;
        String applicationName = null;
        NonPersistentCache overlayCache = null;

        try {
            overlayCache = container.adapt(NonPersistentCache.class);
        } catch (UnableToAdaptException e) {
            e.printStackTrace();
        }

        if (overlayCache != null) {
            WebModuleInfo moduleInfo = (WebModuleInfo) overlayCache.getFromCache(WebModuleInfo.class);
            /*
             * We don't know if the current Container/NonPersistentcache can get us a WebModuleInfo
             * Hence the null check!
             */
            if (moduleInfo != null) {
                contextRoot = moduleInfo.getContextRoot();
                applicationName = moduleInfo.getName();
                if (contextRoot != null && applicationName != null) {
                    contextRoot_Map.put(applicationName, contextRoot);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void applicationStarted(ApplicationInfo appInfo) throws StateChangeException {}

    /** {@inheritDoc} */
    @Override
    public void applicationStopping(ApplicationInfo appInfo) {}

    /** {@inheritDoc} */
    @Override
    public void applicationStopped(ApplicationInfo appInfo) {
        MetricRegistry registry = sharedMetricRegistry.getOrCreate(MetricRegistry.Type.APPLICATION.getName());
        if (MetricRegistryImpl.class.isInstance(registry)) {
            MetricRegistryImpl impl = (MetricRegistryImpl) registry;
            impl.unRegisterApplicationMetrics(appInfo.getDeploymentName());
        }

        //need to unregsiter MetricIDs with this tag.
    }

    @Reference
    public void getSharedMetricRegistries(SharedMetricRegistries sharedMetricRegistry) {
        this.sharedMetricRegistry = sharedMetricRegistry;
    }
}
