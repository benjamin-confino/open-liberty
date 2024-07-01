package com.ibm.ws.cdi.impl.weld;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.cdi.CDIException;
import com.ibm.ws.cdi.internal.interfaces.ArchiveType;
import com.ibm.ws.cdi.internal.interfaces.CDIArchive;
import com.ibm.ws.cdi.internal.interfaces.WebSphereBeanDeploymentArchive;

/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
//this class exists to be extended by unit tests
public abstract class AbstractBeanDeploymentArchive implements WebSphereBeanDeploymentArchive {

    //use the real impl class because it will look nicer in the logs.
    protected static final TraceComponent tc = Tr.register(BeanDeploymentArchiveImpl.class);

    protected boolean scanned = false;

    //only those classes which are beans are actually loaded ... stored in an ordered map to make debug easier
    protected final Map<String, Class<?>> beanClasses = new TreeMap<String, Class<?>>();

    protected final Set<WebSphereBeanDeploymentArchive> accessibleBDAs = new HashSet<WebSphereBeanDeploymentArchive>();
    protected final Set<WebSphereBeanDeploymentArchive> descendantBDAs = new HashSet<WebSphereBeanDeploymentArchive>();

    protected boolean hasBeans = false;

    protected CDIArchive archive;

    //all of the classes ... those in this archive and any additional ones
    protected final Set<String> allClasses = new HashSet<String>();

    @Override
    public void scan() throws CDIException {
        if (!this.scanned) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                Tr.debug(tc, "scan [ " + getHumanReadableName() + " ] BEGIN SCAN");
            }
            //mark as scanned up front to prevent loops
            this.scanned = true;

            // Scan any accessible BDAs first to ensure we scan more visible things (shared libs, ear libs) before less visible things (war classes, war libs)
            // This helps to make sure that the later call to isAccessibleBean works
            // Don't scan accessible BDAs of runtime extensions because they sit outside the hierarchy and some of them need to see everything
            if (getType() != ArchiveType.RUNTIME_EXTENSION) {
                for (WebSphereBeanDeploymentArchive child : accessibleBDAs) {
                    if (!child.hasBeenScanned()) {
                        child.scan();
                    }
                }
            }

            scanForBeanClassNames();
        }

    }

    @Override
    public boolean hasBeenScanned() {
        return scanned;
    }

    protected boolean isAccessibleBean(Class<?> beanClass) {
        boolean accessibleBean = false;
        for (WebSphereBeanDeploymentArchive child : accessibleBDAs) {
            if (child.containsBeanClass(beanClass)) {
                accessibleBean = true;
                break;
            }
        }
        return accessibleBean;
    }

    protected abstract void initializeJEEComponentClasses(Set<String> allClasses2) throws CDIException;

    protected abstract void initializeInjectionClasses(Collection<Class<?>> values) throws CDIException;

    protected abstract void scanForEndpoints() throws CDIException;

    protected abstract Set<String> scanForBeanClassNames() throws CDIException;

    @Override
    public abstract CDIArchive getArchive();

    protected abstract String getHumanReadableName();

    protected abstract Set<WebSphereBeanDeploymentArchive> getDescendetBDAs();

}
