package com.ibm.ws.cdi.impl.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.ibm.ws.cdi.CDIException;
import com.ibm.ws.cdi.internal.interfaces.ArchiveType;
import com.ibm.ws.cdi.internal.interfaces.WebSphereBeanDeploymentArchive;

import org.junit.Test;

public class BDAScanningTest {

    @Test
    public void testBDAScaninngOrdering() throws CDIException {

        Set<MockBeanDeploymentArchive> allBDAs = new HashSet<MockBeanDeploymentArchive>();

        //create a root archive
        MockBeanDeploymentArchive root = new MockBeanDeploymentArchive(ArchiveType.WEB_MODULE, 15, 15);
        allBDAs.add(root);

        //create a bunch of mock libs
        Set<MockBeanDeploymentArchive> libs = new HashSet<MockBeanDeploymentArchive>();
        for (int i = 0; i < 10; i++) {
            libs.add(new MockBeanDeploymentArchive(ArchiveType.SHARED_LIB, 5, 14));
        }
        allBDAs.addAll(libs);

        //Shared libs can all see each other.
        for (MockBeanDeploymentArchive bda : libs) {
            libs.stream().filter(lib -> !lib.equals(bda)).forEach(lib -> lib.addDescendantBda(bda));
        }

        //create a bunch of runtime extensions configured to see app beans
        Set<MockBeanDeploymentArchive> runtimeExtensions = new HashSet<MockBeanDeploymentArchive>();
        for (int i = 0; i < 3; i++) {
            runtimeExtensions.add(new MockBeanDeploymentArchive(ArchiveType.RUNTIME_EXTENSION, 1, 4));
        }

        //they can all see each other.
        for (MockBeanDeploymentArchive bda : runtimeExtensions) {
            runtimeExtensions.stream().filter(ext -> !ext.equals(bda)).forEach(ext -> ext.addDescendantBda(bda));
        }

        //they can see all app beans.
        for (MockBeanDeploymentArchive bda : allBDAs) {
            runtimeExtensions.stream().forEach(ext -> ext.addDescendantBda(bda));
        }

        //and app beans can see them
        for (MockBeanDeploymentArchive bda : runtimeExtensions) {
            allBDAs.stream().forEach(ext -> ext.addDescendantBda(bda));
        }
        allBDAs.addAll(runtimeExtensions);

        MockWebSphereCDIDeployment deployment = new MockWebSphereCDIDeployment(new ArrayList<WebSphereBeanDeploymentArchive>(allBDAs));
        deployment.scan();

        allBDAs.stream().forEach(bda -> bda.seenInAcceptableOrder());

    }

}
