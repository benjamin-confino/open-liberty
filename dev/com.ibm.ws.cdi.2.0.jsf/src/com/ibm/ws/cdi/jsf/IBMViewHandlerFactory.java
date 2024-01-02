/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.cdi.jsf;

import javax.faces.application.ViewHandler;

/**
 * Allows us to create IBMViewHandler objects without referencing the class directly in JSF packages. This avoids the following compile error:
  
  open-liberty/dev/com.ibm.ws.jsfContainer/src/com/ibm/ws/jsf/container/application/JSFContainerApplication.java:47: error: cannot access ConversationAwareViewHandler
                delegate.setViewHandler(new IBMViewHandler(handler, appname));
                        ^
  class file for org.jboss.weld.jsf.ConversationAwareViewHandler not found

 */
public interface IBMViewHandlerFactory {

    public IBMViewHandler createIBMViewHandler(ViewHandler delegate, String contextID);
}
