/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.microprofile.telemetry.internal.utils.otelCollector;

import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonValue;

import org.hamcrest.Matcher;

import com.ibm.websphere.simplicity.log.Log;

import componenttest.topology.utils.HttpRequest;

/**
 * Client for retrieving spans from the Zipkin trace server
 * <p>
 * This calls the HTTP API documented at https://zipkin.io/zipkin-api/#/
 */
public class OtelCollectorQueryClient {

    private static final Class<OtelCollectorQueryClient> c = OtelCollectorQueryClient.class;

    private final String baseUrl;

    public OtelCollectorQueryClient(OtelCollectorContainer container) {
        baseUrl = container.getApiBaseUrl();
    }

    public void dumpMetrics() throws Exception {
        HttpRequest req = new HttpRequest(baseUrl + "/metrics");
        System.out.println("Eminem: " + req.run(String.class));
    }
    

}
