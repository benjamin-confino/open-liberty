/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/**
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.microprofile.metrics.internal.fat.histogram;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;

@ApplicationScoped
@Path("/test")
public class MetricsResource {

	Timer timer;

	@Inject
	MetricRegistry mr;

	@GET
	@Path("/getHistograms")
	public String badHistogramPercentiles() throws InterruptedException {
		return "Histogram registered";
	}

	@Inject
	@Metric(name = "injectedHistogramCustomPercentiles")
	private Histogram injectedHistogramCustomPercentiles;

	@Inject
	@Metric(name = "injectedHistogramNoPercentiles")
	private Histogram injectedHistogramNoPercentiles;

	@Inject
	@Metric(name = "injectedTimerNoPercentiles")
	private Timer injectedTimerNoPercentiles;

	@Inject
	@Metric(name = "injectedHistogramCustomBucketsDefaultPercentiles")
	private Histogram injectedHistogramCustomBucketsDefaultPercentiles;

	@Inject
	@Metric(name = "injectedHistogramCustomBucketsCustomPercentiles")
	private Histogram injectedHistogramCustomBucketsCustomPercentiles;

	@Inject
	@Metric(name = "injectedHistogramCustomBucketsNoPercentiles")
	private Histogram injectedHistogramCustomBucketsNoPercentiles;

	@Inject
	@Metric(name = "injected.precedence.histogram", absolute = true)
	private Histogram injectedPrecedenceHistogram;

	@Inject
	@Metric(name = "injected.precedence.override.histogram", absolute = true)
	private Histogram injectedPrecedenceOverrideHistogram;

}
