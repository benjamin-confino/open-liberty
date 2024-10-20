/*******************************************************************************
 * Copyright (c) 2020, 2021 IBM Corporation and others.
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

package com.ibm.ws.jpa.fvt.callback.entities.entitydeclared.xml;

import com.ibm.ws.jpa.fvt.callback.entities.AbstractCallbackEntity;

public class XMLCallbackPrivateEntity extends AbstractCallbackEntity {
    public XMLCallbackPrivateEntity() {
        super();
    }

    @SuppressWarnings("unused")
    private void prePersistCallback() {
        doPrePersist(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void postPersistCallback() {
        doPostPersist(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void preUpdateCallback() {
        doPreUpdate(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void postUpdateCallback() {
        doPostUpdate(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void preRemoveCallback() {
        doPreRemove(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void postRemoveCallback() {
        doPostRemove(ProtectionType.PT_PRIVATE);
    }

    @SuppressWarnings("unused")
    private void postLoadCallback() {
        doPostLoad(ProtectionType.PT_PRIVATE);
    }

    @Override
    public String toString() {
        return "CallbackPrivateEntity [id=" + getId() + ", name=" + getName() + "]";
    }
}
