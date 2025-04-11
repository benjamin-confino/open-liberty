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

import org.osgi.service.component.annotations.Component;

import io.openliberty.microprofile.telemetry.internal.common.semconv.SemcovConstantsAccessor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.SemanticAttributes;

@Component
public class SemconvConstantsAccessorImpl implements SemcovConstantsAccessor {

    @Override
    public AttributeKey<String> errorType() {
        return SemanticAttributes.ERROR_TYPE;
    }

    @Override
    public AttributeKey<String> httpRequestMethod() {
        return SemanticAttributes.HTTP_REQUEST_METHOD;
    }

    @Override
    public AttributeKey<String> accessRequestHost() { //TODO fix method name
        return SemanticAttributes.SERVER_ADDRESS;
    }

    @Override
    public AttributeKey<String> clientAddress() {
        return SemanticAttributes.CLIENT_ADDRESS;
    }

    @Override
    public AttributeKey<Long> httpResponseStatusCode() {
        return SemanticAttributes.HTTP_RESPONSE_STATUS_CODE;
    }

    @Override
    public AttributeKey<Long> localNetworkPort() {
        return SemanticAttributes.NETWORK_LOCAL_PORT;
    }

    @Override
    public AttributeKey<String> networkPeerAddress() {
        return SemanticAttributes.NETWORK_PEER_ADDRESS;
    }

}
