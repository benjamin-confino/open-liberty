package io.openliberty.checkpoint.fat;

import static org.junit.Assert.assertNotNull;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;

import componenttest.annotation.Server;
import componenttest.annotation.SkipIfCheckpointNotSupported;
import componenttest.annotation.TestServlet;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import io.openliberty.checkpoint.spi.CheckpointPhase;
import mpReactiveMessaging.SimpleReactiveMessagingServlet;

@RunWith(FATRunner.class)
@SkipIfCheckpointNotSupported
public class MPReactiveMessagingTest extends FATServletClient {

    public static final String APP_NAME = "BasicMPReactiveApp";
    public static final String SERVER_NAME = "SimpleRxMessagingServer";

    @Server(SERVER_NAME)
    @TestServlet(servlet = SimpleReactiveMessagingServlet.class, contextRoot = APP_NAME)
    public static LibertyServer server;

    @BeforeClass
    public static void copyAppToDropins() throws Exception {
        WebArchive war = ShrinkWrap.create(WebArchive.class, APP_NAME + ".war")
                        .addPackages(true, SimpleReactiveMessagingServlet.class.getPackage());

        ShrinkHelper.exportDropinAppToServer(server, war, DeployOptions.SERVER_ONLY);
    }

    @Before
    public void setUp() throws Exception {
        server.setCheckpoint(CheckpointPhase.AFTER_APP_START, true,
                             server -> {
                                 assertNotNull("'SRVE0169I: Loading Web Module: " + APP_NAME + "' message not found in log before rerstore",
                                               server.waitForStringInLogUsingMark("SRVE0169I: .*" + APP_NAME, 0));
                                 assertNotNull("'CWWKZ0001I: Application " + APP_NAME + " started' message not found in log.",
                                               server.waitForStringInLogUsingMark("CWWKZ0001I: .*" + APP_NAME, 0));
                             });
        server.startServer();

    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer();
    }

}
