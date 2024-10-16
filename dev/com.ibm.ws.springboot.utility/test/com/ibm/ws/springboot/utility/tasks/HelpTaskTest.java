/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
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
package com.ibm.ws.springboot.utility.tasks;

import static org.junit.Assert.assertEquals;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import com.ibm.ws.springboot.utility.SpringBootUtilityTask;
import com.ibm.ws.springboot.utility.utils.ConsoleWrapper;

/**
 *
 */
public class HelpTaskTest {
    private static final String NL = System.getProperty("line.separator");
    private final Mockery mock = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };
    private final ConsoleWrapper stdin = mock.mock(ConsoleWrapper.class, "stdin");
    private final PrintStream stdout = mock.mock(PrintStream.class, "stdout");
    private final PrintStream stderr = mock.mock(PrintStream.class, "stderr");
    private final SpringBootUtilityTask task = mock.mock(SpringBootUtilityTask.class);
    private HelpTask help;

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#getScriptUsage()}.
     */
    @Test
    public void getScriptUsage_noTasks() {
        help = new HelpTask("myScript", new ArrayList<SpringBootUtilityTask>());

        assertEquals("Did not get expected usage statement",
                     NL + "Usage: myScript {} [options]" + NL,
                     help.getScriptUsage());
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#getScriptUsage()}.
     */
    @Test
    public void getScriptUsage_justHelp() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        assertEquals("Did not get expected usage statement",
                     NL + "Usage: myScript {" + help.getTaskName() + "} [options]" + NL,
                     help.getScriptUsage());
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#getScriptUsage()}.
     */
    @Test
    public void getScriptUsage_registeredTasks() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        SpringBootUtilityTask thin = new ThinAppTask(null, "myScript");
        tasks.add(thin);
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        final String expectedMsg = NL + "Usage: myScript {" +
                                   thin.getTaskName() + "|help} [options]" + NL;
        assertEquals("Did not get expected usage statement",
                     expectedMsg,
                     help.getScriptUsage());
    }

    @Test
    public void getTaskUsage() {
        help = new HelpTask("myScript", new ArrayList<SpringBootUtilityTask>());
        mock.checking(new Expectations() {
            {
                one(task).getTaskName();
                will(returnValue("mockTask"));
                one(task).getTaskHelp();
                will(returnValue("mockHelp"));
            }
        });

        final String expectedMsg = NL + NL + "mockHelp";
        assertEquals("Did not get expected usage statement",
                     expectedMsg,
                     help.getTaskUsage(task));
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#handleTask(ConsoleWrapper, PrintStream, PrintStream, String[])}.
     */
    @Test
    public void handleTask_verboseHelp() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        SpringBootUtilityTask thin = new ThinAppTask(null, "myScript");
        tasks.add(thin);
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        final String expectedMsg = NL + "Usage: myScript {thin|help} [options]" + NL
                                   + NL + "Actions:" + NL
                                   + NL + "    " + thin.getTaskName() + NL + thin.getTaskDescription() + NL
                                   + NL + "    " + help.getTaskName() + NL + help.getTaskDescription() + NL
                                   + NL + "Options:" + NL + "\tUse help [actionName] for detailed option information of each action.";
        mock.checking(new Expectations() {
            {
                one(stdout).println(expectedMsg);
            }
        });

        String[] args = new String[] { "help" };
        help.handleTask(stdin, stdout, stderr, args);
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#handleTask(ConsoleWrapper, PrintStream, PrintStream, String[])}.
     */
    @Test
    public void handleTask_unknownTask() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        mock.checking(new Expectations() {
            {
                one(stderr).println(NL + "Unknown task: unknownTask" + NL);
            }
        });

        String[] args = new String[] { "help", "unknownTask" };
        help.handleTask(stdin, stdout, stderr, args);
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#handleTask(ConsoleWrapper, PrintStream, PrintStream, String[])}.
     */
    @Test
    public void handleTask_help() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        mock.checking(new Expectations() {
            {
                one(stdout).println(NL + help.getTaskHelp() + NL);
            }
        });

        String[] args = new String[] { "help", "help" };
        help.handleTask(stdin, stdout, stderr, args);
    }

    /**
     * Test method for {@link com.ibm.ws.security.utility.tasks.HelpTask#handleTask(ConsoleWrapper, PrintStream, PrintStream, String[])}.
     */
    @Test
    public void handleTask_knownTask() {
        List<SpringBootUtilityTask> tasks = new ArrayList<SpringBootUtilityTask>();
        tasks.add(task);
        help = new HelpTask("myScript", tasks);
        tasks.add(help);

        mock.checking(new Expectations() {
            {
                one(task).getTaskName();
                will(returnValue("mockTask"));
                one(task).getTaskHelp();
                will(returnValue("mock help"));
                one(stdout).println(NL + "mock help" + NL);
            }
        });
        String[] args = new String[] { "help", "mockTask" };
        help.handleTask(stdin, stdout, stderr, args);

    }
}
