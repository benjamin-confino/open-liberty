/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.phonehome;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import com.ibm.websphere.kernel.server.ServerInfoMBean;
import com.ibm.ws.kernel.feature.FeatureProvisioner;
import com.ibm.ws.kernel.feature.ServerStartedPhase2;

import io.openliberty.checkpoint.spi.CheckpointHook;
import io.openliberty.checkpoint.spi.CheckpointPhase;

/**
 * The main entry point into the Phone Home service
 */
@Component(immediate = true, configurationPid = "com.ibm.ws.kernel.phonehome.phoneHomeConfig", 
configurationPolicy = ConfigurationPolicy.OPTIONAL )
public class PhoneHomeEntryPoint {


    /**
     * Checks server.xml, bootstrap properties, for the following two conditions:
     * 1) Phone home has not been explicitly disabled in any file
     * 2) Phone home has been explicitly enabled in at least one file
     *
     * sets phoneHomeEnabled to true if both conditions are satisfied, otherwise false
     */
    //The server.xml's contents are passed in as properties. 
    @Activate
    public PhoneHomeEntryPoint(ComponentContext ignored, Map<String, Object> properties,
    		//TODO this is not dynamic because it has to be finalized to pass it into checkpoint. Maybe get it at the point of use instead.
    		@Reference(cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY) final List<PhoneHomeDataSource> dataSources,
    		@Reference ServerStartedPhase2 justAMarker,
    		@Reference final FeatureProvisioner featureProvisioner,
    		@Reference final ServerInfoMBean serverInfo
    ) {
    	PhoneHomeMain main = new PhoneHomeMain(properties, dataSources, featureProvisioner, serverInfo);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(main);

    }
}
