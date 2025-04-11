/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.microprofile.telemetry20.internal.helper;

import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;

import org.osgi.service.component.annotations.Component;

import io.openliberty.microprofile.telemetry.internal.common.semconv.SemcovConstantsAccessor;
import io.opentelemetry.api.common.AttributeKey;

@Component
public class SemconvConstantsAccessorImpl implements SemcovConstantsAccessor {

    @Override
    public AttributeKey<String> errorType() {
        return ERROR_TYPE;
    }

    @Override
    public AttributeKey<String> httpRequestMethod() {
        return HTTP_REQUEST_METHOD;
    }

    @Override
    public AttributeKey<String> accessRequestHost() { //TODO fix method name
        return SERVER_ADDRESS;
    }

    @Override
    public AttributeKey<String> clientAddress() {
        return CLIENT_ADDRESS;
    }

    @Override
    public AttributeKey<Long> httpResponseStatusCode() {
        return HTTP_RESPONSE_STATUS_CODE;
    }

    @Override
    public AttributeKey<Long> localNetworkPort() {
        return NETWORK_LOCAL_PORT;
    }

    @Override
    public AttributeKey<String> networkPeerAddress() {
        return NETWORK_PEER_ADDRESS;
    }

}
