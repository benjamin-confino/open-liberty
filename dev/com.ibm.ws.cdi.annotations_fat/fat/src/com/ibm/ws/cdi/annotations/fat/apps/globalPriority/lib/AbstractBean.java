/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
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
package com.ibm.ws.cdi.annotations.fat.apps.globalPriority.lib;

import javax.enterprise.context.RequestScoped;

import com.ibm.ws.cdi.annotations.fat.apps.utils.ChainableList;
import com.ibm.ws.cdi.annotations.fat.apps.utils.ChainableListImpl;
import com.ibm.ws.cdi.annotations.fat.apps.utils.Intercepted;


@RequestScoped
public abstract class AbstractBean implements Bean {

    @Override
    public ChainableList<String> getDecorators() {
        return new ChainableListImpl<String>();
    }

    @Intercepted
    @Override
    public ChainableList<String> getInterceptors() {
        return new ChainableListImpl<String>();
    }

    public Class<? extends AbstractBean> getBean() {
        return this.getClass();
    }

}
