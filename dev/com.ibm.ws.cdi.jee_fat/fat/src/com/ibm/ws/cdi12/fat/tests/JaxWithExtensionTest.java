/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.cdi12.fat.tests;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.ws.fat.util.LoggingTest;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import com.ibm.ws.fat.util.LoggingTest;
import com.ibm.ws.fat.util.SharedServer;
import com.ibm.ws.fat.util.browser.WebBrowser;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

//@Mode(TestMode.FULL)
public class JaxWithExtensionTest extends LoggingTest {

    public static LibertyServer server;

    protected SharedServer getSharedServer() {
        return null;
    }

    @BeforeClass
    public static void setUp() throws Exception {

        WebArchive jaxwsWithExtensionApp = ShrinkWrap.create(WebArchive.class, "jaxrsWithExtension.war")
                        .addPackage("com.cdryx")
                        .add(new FileAsset(new File("test-applications/jaxrsWithExtension.war/resources/WEB-INF/lib/deltaspike-core-api-1.9.4.jar")), "/WEB-INF/lib/deltaspike-core-api-1.9.4.jar")
                        .add(new FileAsset(new File("test-applications/jaxrsWithExtension.war/resources/WEB-INF/lib/deltaspike-core-impl-1.9.4.jar")), "/WEB-INF/lib/deltaspike-core-impl-1.9.4.jar")
                        .add(new FileAsset(new File("test-applications/jaxrsWithExtension.war/resources/WEB-INF/beans.xml")), "/WEB-INF/beans.xml")
                        .add(new FileAsset(new File("test-applications/jaxrsWithExtension.war/resources/META-INF/services/javax.enterprise.inject.spi.Extension")), "/META-INF/services/javax.enterprise.inject.spi.Extension");

        server = LibertyServerFactory.getLibertyServer("jaxWithExtensionServer");
        ShrinkHelper.exportDropinAppToServer(server, jaxwsWithExtensionApp);
        server.startServer();
        server.waitForStringInLogUsingMark("CWWKZ0001I.*Application jaxrsWithExtension started");
    }

    //@Test
    public void testExtensionModifiesJaxPathClass() throws Exception {
        HttpUtils.findStringInUrl(server, "/jaxrsWithExtension/cdiext/simplerestapi", "This is ok");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stopServer();
        }
    }

}
