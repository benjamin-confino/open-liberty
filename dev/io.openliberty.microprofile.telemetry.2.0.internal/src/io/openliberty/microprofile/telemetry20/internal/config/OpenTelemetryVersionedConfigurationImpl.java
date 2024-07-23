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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.lang3.SystemUtils;
import org.osgi.service.component.annotations.Component;

import com.ibm.websphere.kernel.server.ServerInfoMBean;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.kernel.service.util.ServiceCaller;

import io.openliberty.microprofile.telemetry.internal.common.AgentDetection;
import io.openliberty.microprofile.telemetry.internal.common.constants.OpenTelemetryConstants;
import io.openliberty.microprofile.telemetry.internal.common.info.OpenTelemetryInfoFactoryImpl;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
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

    private static final ServiceCaller<ServerInfoMBean> infoBeanCaller = new ServiceCaller<ServerInfoMBean>(OpenTelemetryVersionedConfigurationImpl.class, ServerInfoMBean.class);

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

    //TODO We really need to de-deuplicate this code that is also in OpenTelemetryInfoFactoryImpl.
    //But that will require also moving createServerOpenTelemetryInfo into the factory and I'm told
    //Its here to work around a problem. Once we get everything working we need to come back and clean this up
    @FFDCIgnore(UnknownHostException.class)
    private Resource customizeResource(Resource resource, ConfigProperties c) {
        ResourceBuilder builder = resource.toBuilder();
        builder.put(OpenTelemetryConstants.KEY_SERVICE_NAME, OpenTelemetryConstants.OTEL_RUNTIME_INSTANCE_NAME);

        //TODO these are required, but its unclear how we'd get them. Especially in runtime mode!
        //service.instance.id
        builder.put(OpenTelemetryConstants.KEY_SERVICE_NAME, "unknown_service");

        //TODO - do we provide container IDs? A quick check doesn't find Liberty acquiring them anywhere else
        //container.id

        // resources for HOST
        builder.put(OpenTelemetryConstants.KEY_HOST_ARCH, System.getProperty("os.arch") + System.getProperty("sun.arch.data.model"));
        try {
            builder.put(OpenTelemetryConstants.KEY_HOST_NAME, InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            builder.put(OpenTelemetryConstants.KEY_HOST_NAME, "Unkown");
        }

        // resources for OS
        builder.put(OpenTelemetryConstants.KEY_OS_DESCRIPTION, SystemUtils.OS_VERSION); //TODO test if this is actually good
        builder.put(OpenTelemetryConstants.KEY_OS_TYPE, SystemUtils.OS_NAME);

        // //resources for java process
        RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
        List<String> commandLine = mxBean.getInputArguments();
        builder.put(OpenTelemetryConstants.KEY_PROCESS_COMMAND, commandLine.get(0));
        builder.put(OpenTelemetryConstants.KEY_PROCESS_COMMAND_ARGS, commandLine);
        builder.put(OpenTelemetryConstants.KEY_PROCESS_COMMAND_NAME, String.join(" ", commandLine));
        builder.put(OpenTelemetryConstants.KEY_PROCESS_OWNER, System.getProperty("user.name"));

        //TODO see if I can find a reliable way to do this on java8
        //        process.executable.path: Str(/opt/java/openjdk/bin/java)
        builder.put(OpenTelemetryConstants.KEY_PROCESS_PID, mxBean.getPid());
        //TODO if possible add the parent PID.
        builder.put(OpenTelemetryConstants.KEY_PROCESS_RUNTIME_NAME, mxBean.getName());
        builder.put(OpenTelemetryConstants.KEY_PROCESS_RUNTIME_DESCRIPTION, mxBean.getVmVendor() + " " + mxBean.getVmName() + " " + mxBean.getVmVersion());
        builder.put(OpenTelemetryConstants.KEY_PROCESS_RUNTIME_VERSION, mxBean.getVmVersion());

        //Resources for Open Telemetry itself
        builder.put(OpenTelemetryConstants.KEY_TELEMETRY_DISTRO_NAME, OpenTelemetryConstants.INSTRUMENTATION_NAME);

        builder.put(OpenTelemetryConstants.KEY_TELEMETRY_VERSION, infoBeanCaller.call(ServerInfoMBean::getLibertyVersion).get());

        // The following are provided automatically by the sdk
        // telemetry.sdk.language
        // telemetry.sdk.name
        // telemetry.sdk.version

        return builder.build();
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
