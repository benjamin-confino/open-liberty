/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
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
package com.ibm.websphere.microprofile.faulttolerance.metrics.fat.tests.circuitbreaker;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;

import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.Test;

import com.ibm.websphere.microprofile.faulttolerance.metrics.fat.tests.util.InMemoryMetricExporter;

import componenttest.app.FATServlet;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;

@WebServlet("/circuit-breaker-metric")
@Mode(TestMode.FULL) //This test uses a lot of waits
public class CircuitBreakerMetricServlet extends FATServlet {

    private static String FAULT_TOLERENCE_PREFIX = "ft";
    private static String CLASS = CircuitBreakerMetricBean.class.getCanonicalName();
    private static String METHOD = "doWorkWithExeception";

    private static final long serialVersionUID = 1L;

    @Inject
    private CircuitBreakerMetricBean testBean;

    @Inject
    private Meter meter;

    @Inject
    InMemoryMetricExporter exporter;

    private long callsSucceeded = 0;
    private long callsFailed = 0;
    private long callsPrevented = 0;

    @Test
    public void testCircuitBreakerMetrics() {
        String methodName = CircuitBreakerMetricBean.class.getCanonicalName() + ".doWorkWithExeception";

        exporter.reset();

        // Note: Circuit Breaker under test will open if 2/4 requests are failures
        // Only requests which throw a TestException are considered failures

        // Failing call (exception is in failOn)

        // Succeeding call (no exception) This goes first because it makes the logic for verifying the number of results simpler
        testBean.slackOf();
        testBean.doWorkWithExeception(null);
        populateLatestResult();
        assertEquals("incorrect calls succeeded", 1, callsSucceeded);
        assertEquals("incorrect calls failed", 0, callsFailed);
        assertEquals("incorrect calls prevented", 0, callsPrevented);

        try {
            testBean.slackOf();
            testBean.doWorkWithExeception(new TestException());
        } catch (TestException e) {
        }
        populateLatestResult();
        assertEquals("incorrect calls succeeded", 1, callsSucceeded);
        assertEquals("incorrect calls failed", 1, callsFailed);
        assertEquals("incorrect calls prevented", 0, callsPrevented);

        // Succeeding call that threw exception (exception is not in failOn)
        try {
            testBean.slackOf();
            testBean.doWorkWithExeception(new RuntimeException());
        } catch (RuntimeException e) {
        }
        populateLatestResult();
        assertEquals("incorrect calls succeeded", 2, callsSucceeded);
        assertEquals("incorrect calls failed", 1, callsFailed);
        assertEquals("incorrect calls prevented", 0, callsPrevented);

        // Failing call (to open the breaker)
        try {
            testBean.slackOf();
            testBean.doWorkWithExeception(new TestException());
        } catch (TestException e) {
        }
        populateLatestResult();
        assertEquals("incorrect calls succeeded", 2, callsSucceeded);
        assertEquals("incorrect calls failed", 2, callsFailed);
        assertEquals("incorrect calls prevented", 0, callsPrevented);

        // Prevented call (breaker is open)
        try {
            testBean.slackOf();
            testBean.doWorkWithExeception(new TestException());
        } catch (CircuitBreakerOpenException e) {
        }
        populateLatestResult();
        assertEquals("incorrect calls succeeded", 2, callsSucceeded);
        assertEquals("incorrect calls failed", 2, callsFailed);
        assertEquals("incorrect calls prevented", 1, callsPrevented);

    }

    private void populateLatestResult() {
        AttributeKey<String> methodKey = AttributeKey.stringKey("method");

        String classAndMethod = CircuitBreakerMetricBean.class.getName() + ".doWorkWithExeception";

        List<MetricData> metricItems = exporter.getFinishedMetricItems();
        for (MetricData data : metricItems) {

            System.out.println("GREP - " + data.getName());

            if (data.getName().equals(FAULT_TOLERENCE_PREFIX + ".circuitbreaker.callsFailed.total")) {
                if (data.getLongSumData().getPoints().size() != 1) {
                    throw new IllegalStateException(FAULT_TOLERENCE_PREFIX + ".circuitbreaker.callsFailed.total had too many values");
                }

                List<LongPointData> callsFailedList = data.getLongSumData().getPoints().stream()
                                .filter(lpd -> lpd.getAttributes().get(methodKey).equals(classAndMethod))
                                .collect(Collectors.toList());

                assertEquals(1, callsFailedList.size());
                callsFailed = callsFailedList.get(0).getValue();
            }

            if (data.getName().equals(FAULT_TOLERENCE_PREFIX + ".circuitbreaker.callsSucceeded.total")) {
                if (data.getLongSumData().getPoints().size() != 2) {
                    throw new IllegalStateException("FAULT_TOLERENCE_PREFIX + circuitbreaker.callsSucceeded.total had " + data.getLongSumData().getPoints().size() + " values");
                }

                List<LongPointData> callsSucceededList = data.getLongSumData().getPoints().stream()
                                .filter(lpd -> lpd.getAttributes().get(methodKey).equals(classAndMethod))
                                .collect(Collectors.toList());

                assertEquals(1, callsSucceededList.size());
                callsSucceeded = callsSucceededList.get(0).getValue();

            }

            if (data.getName().equals(FAULT_TOLERENCE_PREFIX + ".circuitbreaker.callsPrevented.total")) {
                if (data.getLongSumData().getPoints().size() != 1) {
                    throw new IllegalStateException(FAULT_TOLERENCE_PREFIX + ".circuitbreaker.callsPrevented.total had too many values");
                }
                List<LongPointData> callsPreventedList = data.getLongSumData().getPoints().stream()
                                .filter(lpd -> lpd.getAttributes().get(methodKey).equals(classAndMethod))
                                .collect(Collectors.toList());

                assertEquals(1, callsPreventedList.size());
                callsPrevented = callsPreventedList.get(0).getValue();
            }
        }
        exporter.reset();
    }

}
