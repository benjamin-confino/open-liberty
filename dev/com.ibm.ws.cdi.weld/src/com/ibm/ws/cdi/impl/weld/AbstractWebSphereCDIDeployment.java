package com.ibm.ws.cdi.impl.weld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ibm.ws.cdi.CDIException;
import com.ibm.ws.cdi.internal.interfaces.WebSphereBeanDeploymentArchive;
import com.ibm.ws.cdi.internal.interfaces.WebSphereCDIDeployment;

public abstract class AbstractWebSphereCDIDeployment implements WebSphereCDIDeployment {

    protected final Map<String, WebSphereBeanDeploymentArchive> deploymentDBAs = new HashMap<String, WebSphereBeanDeploymentArchive>();

    /**
     * Scan all the BDAs in the deployment to see if there are any bean classes.
     *
     * This method must be called before scanForEjbEndpoints() and before we try to do
     * any real work with the deployment or the BDAs
     *
     * @throws CDIException
     */
    @Override
    public void scan() throws CDIException {
        Collection<WebSphereBeanDeploymentArchive> allBDAs = new ArrayList<WebSphereBeanDeploymentArchive>(deploymentDBAs.values());
        for (WebSphereBeanDeploymentArchive bda : allBDAs) {
            bda.scanForBeanDefiningAnnotations(true);
        }
        for (WebSphereBeanDeploymentArchive bda : allBDAs) {
            if (!bda.hasBeenScanned()) {
                bda.scan();
            }
        }
    }

    protected abstract Collection<WebSphereBeanDeploymentArchive> getAllBDAs();
}
