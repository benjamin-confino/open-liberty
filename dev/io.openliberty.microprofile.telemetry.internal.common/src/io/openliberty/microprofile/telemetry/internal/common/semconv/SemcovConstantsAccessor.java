/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.microprofile.telemetry.internal.common.semconv;

import io.opentelemetry.api.common.AttributeKey;

//An interface for hiding the churn in semconv package names
public interface SemcovConstantsAccessor {

    public AttributeKey<String> accessRequestHost();

    public AttributeKey<String> clientAddress();

    public AttributeKey<String> errorType();

    public AttributeKey<String> httpRequestMethod();

    public AttributeKey<Long> httpResponseStatusCode();

    public AttributeKey<Long> localNetworkPort();

    public AttributeKey<String> networkPeerAddress();

}
