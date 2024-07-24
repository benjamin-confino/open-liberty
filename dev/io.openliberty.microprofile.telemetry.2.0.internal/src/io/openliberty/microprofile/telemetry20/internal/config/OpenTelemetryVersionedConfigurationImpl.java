/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.microprofile.telemetry20.internal.config;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import org.osgi.service.component.annotations.Component;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;

import io.openliberty.microprofile.telemetry.internal.common.AgentDetection;
import io.openliberty.microprofile.telemetry.internal.common.constants.OpenTelemetryConstants;
import io.openliberty.microprofile.telemetry.internal.common.info.OpenTelemetryInfoFactoryImpl;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.instrumentation.resources.OsResource;
import io.opentelemetry.instrumentation.resources.ProcessResource;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResource;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;

/**
 * This class contains version specific configuration for OpenTelemetryInfoFactory
 */
@Component
public class OpenTelemetryVersionedConfigurationImpl implements OpenTelemetryInfoFactoryImpl.OpenTelemetryVersionedConfiguration {

    private static final TraceComponent tc = Tr.register(OpenTelemetryVersionedConfigurationImpl.class);

    private static final String OS_BEAN_J9 = "com.ibm.lang.management.OperatingSystemMXBean";
    private static final String OS_BEAN_HOTSPOT = "com.sun.management.OperatingSystemMXBean";

    // Version specific API calls to AutoConfiguredOpenTelemetrySdk.builder()
    @Override
    public OpenTelemetry buildOpenTelemetry(Map<String, String> openTelemetryProperties,
                                            BiFunction<? super Resource, ConfigProperties, ? extends Resource> resourceCustomiser, ClassLoader classLoader) {

        openTelemetryProperties.putAll(getTelemetryPropertyDefaults());

        OpenTelemetrySdk openTelemetry = AutoConfiguredOpenTelemetrySdk.builder()
                        .addPropertiesCustomizer(x -> openTelemetryProperties) //Overrides OpenTelemetry's property order
                        .addResourceCustomizer(resourceCustomiser) //Defaults service name to application name
                        .setServiceClassLoader(classLoader)
                        .disableShutdownHook()
                        .build()
                        .getOpenTelemetrySdk();

        return openTelemetry;

    }

    // Version specific default properties
    private Map<String, String> getTelemetryPropertyDefaults() {
        Map<String, String> telemetryProperties = new HashMap<String, String>();
        return telemetryProperties;
    }

    @Override
    public OpenTelemetry createServerOpenTelemetryInfo(HashMap<String, String> telemetryProperties) {
        try {
            String otelInstanceName = OpenTelemetryConstants.OTEL_RUNTIME_INSTANCE_NAME;

            if (AgentDetection.isAgentActive()) {
                // If we're using the agent, it will have set GlobalOpenTelemetry and we must use its instance
                // all config is handled by the agent in this case
                return GlobalOpenTelemetry.get();
            }

            ClassLoader classLoader = OpenTelemetry.noop().getClass().getClassLoader();

            //Builds tracer provider if user has enabled tracing aspects with config properties
            if (!checkDisabled(telemetryProperties)) {
                OpenTelemetry openTelemetry = AccessController.doPrivileged((PrivilegedAction<OpenTelemetry>) () -> {
                    return buildOpenTelemetry(telemetryProperties, this::customizeResource, classLoader);
                });

                if (openTelemetry != null) {

                    if (runningOnJ9OrHotspot()) {
                        // Register observers for runtime metrics
                        Classes.registerObservers(openTelemetry);
                        Cpu.registerObservers(openTelemetry);
                        MemoryPools.registerObservers(openTelemetry);
                        Threads.registerObservers(openTelemetry);
                        GarbageCollector.registerObservers(openTelemetry);
                    }

                    return openTelemetry;
                }
            }

            //By default, MicroProfile Telemetry tracing is off.
            //The absence of an installed SDK is a “no-op” API
            //Operations on a Tracer, or on Spans have no side effects and do nothing

            Tr.info(tc, "CWMOT5100.tracing.is.disabled", otelInstanceName);

            return null;
        } catch (Exception e) {
            Tr.error(tc, Tr.formatMessage(tc, "CWMOT5002.telemetry.error", e));
            return null;
        }
    }

    //TODO This and the next method should be refactored into the factory.
    private Resource customizeResource(Resource resource, ConfigProperties c) {
        resource = mergeInOtelResources(resource);
        ResourceBuilder builder = resource.toBuilder();
        builder.put(AttributeKey.stringKey("service.name"), getServiceName(c));
        builder.put(OpenTelemetryConstants.KEY_SERVICE_INSTANCE_ID, UUID.randomUUID().toString());

        return builder.build();
    }

    private String getServiceName(ConfigProperties c) {
        String serviceName = c.getString(OpenTelemetryConstants.SERVICE_NAME_PROPERTY);
        if (serviceName == null) {
            serviceName = "unkown.service";
        }
        return serviceName;
    }

    private static boolean checkDisabled(Map<String, String> oTelConfigs) {
        //In order to enable any of the tracing aspects, the configuration otel.sdk.disabled=false must be specified in any of the configuration sources available via MicroProfile Config.
        if (oTelConfigs.get(OpenTelemetryConstants.ENV_DISABLE_PROPERTY) != null) {
            return Boolean.valueOf(oTelConfigs.get(OpenTelemetryConstants.ENV_DISABLE_PROPERTY));
        } else if (oTelConfigs.get(OpenTelemetryConstants.CONFIG_DISABLE_PROPERTY) != null) {
            return Boolean.valueOf(oTelConfigs.get(OpenTelemetryConstants.CONFIG_DISABLE_PROPERTY));
        }
        return true;
    }

    @Override
    public Resource mergeInOtelResources(Resource resource) {
        return resource.merge(ContainerResource.get())
                        .merge(HostResource.get())
                        .merge(OsResource.get())
                        .merge(ProcessResource.get())
                        .merge(ProcessRuntimeResource.get());
    }

    @FFDCIgnore(ClassNotFoundException.class)
    private boolean runningOnJ9OrHotspot() {

        Class<?> j9BeanClass = null;
        Class<?> hotspotBeanClass = null;

        try {
            j9BeanClass = Class.forName(OS_BEAN_J9);
        } catch (ClassNotFoundException ignored) {
        }

        try {
            hotspotBeanClass = Class.forName(OS_BEAN_HOTSPOT);
        } catch (ClassNotFoundException ignored) {
        }

        return j9BeanClass != null || hotspotBeanClass != null;
    }

}
