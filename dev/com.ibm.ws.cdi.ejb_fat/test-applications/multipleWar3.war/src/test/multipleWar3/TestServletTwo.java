/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package test.multipleWar3;

import java.io.IOException;
import java.io.PrintWriter;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
@WebServlet("/Twp")
public class TestServletTwo extends HttpServlet {
    //@EJB(name = "MyEjb")
    com.ibm.ws.cdi.lib.two.MyEjb myEjb;

    @Inject
    MyBean myBean;

    /**  */
    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        PrintWriter pw = response.getWriter();
       // pw.println("myEjbInWar3 " + myEjb.getMyEjbName() + " " + myBean.getName());

    }
}
