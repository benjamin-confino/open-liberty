/*******************************************************************************
 * Copyright (c) 2022, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.cdi40.internal.fat.dynamicCDI;

import java.io.File;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;
import com.ibm.ws.fat.util.browser.WebBrowser;
import com.ibm.ws.fat.util.browser.WebBrowserFactory;
import com.ibm.ws.fat.util.browser.WebResponse;

import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.EERepeatActions;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import componenttest.topology.utils.HttpRequest;
import io.openliberty.cdi40.internal.fat.bce.basicwar.BasicBCEExtension;
import io.openliberty.cdi40.internal.fat.bce.basicwar.BasicBCETestServlet;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;

//This tests if we can reload CDI 4.0 without any OSGi issues, see https://github.com/OpenLiberty/open-liberty/issues/26680
@RunWith(FATRunner.class)
public class BuildCompatibleExtensionsDynamicCDITest extends FATServletClient {

    public static final String SERVER_NAME = "cdiBceDynamicCDITestServer";

    @ClassRule
    public static RepeatTests r = EERepeatActions.repeat(SERVER_NAME, EERepeatActions.EE10, EERepeatActions.EE11);

    @Server(SERVER_NAME)
    public static LibertyServer server;

    @BeforeClass
    public static void setup() throws Exception {

        Package warPackage = BasicBCETestServlet.class.getPackage();
        WebArchive basicWar = ShrinkWrap.create(WebArchive.class, "basicWar.war")
                                        .addPackage(warPackage)
                                        .addAsServiceProvider(BuildCompatibleExtension.class, BasicBCEExtension.class)
                                        .addAsWebInfResource(warPackage, "beans.xml", "beans.xml")
                                        .addAsManifestResource(warPackage, "permissions.xml", "permissions.xml"); // Workaround WELD-2705

        ShrinkHelper.exportDropinAppToServer(server, basicWar, DeployOptions.SERVER_ONLY);

        server.startServer();
    }

    @Test
    @Mode(TestMode.FULL)
    @AllowedFFDC("java.lang.NoClassDefFoundError")//A missing CDI class before we enable the CDI feature is expected.
    public void testBCEAndServerRestart() throws Exception {

        server.reconfigureServer("configs/withCDI.xml", "CWWKZ0003I: The application basicWar updated");

        HttpRequest httpRequest = new HttpRequest(server, FATServletClient.getPathAndQuery("basicWar/basicBceTest", "testInjectedBeans"));
        String response = httpRequest.run(String.class);
    }

    @AfterClass
    public static void teardown() throws Exception {
        server.stopServer("CWNEN0047W");
    }

}
