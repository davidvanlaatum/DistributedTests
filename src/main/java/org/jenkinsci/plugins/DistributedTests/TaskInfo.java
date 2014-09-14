/*
 * The MIT License
 *
 * Copyright 2014 David van Laatum.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.DistributedTests;

import java.util.ArrayList;
import java.util.List;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Node;
import javax.annotation.CheckForNull;

/**
 *
 * @author David van Laatum
 */
public class TaskInfo extends Actionable {

  private final DistributedProject project;
  private final DistributedBuild build;
  private final TaskCoordinator.Task task;

  public TaskInfo ( DistributedProject project, DistributedBuild build,
                    TaskCoordinator.Task task ) {
    this.project = project;
    this.build = build;
    this.task = task;
    if ( project == null ) {
      throw new NullPointerException ( "Project must not be null" );
    }
    if ( build == null ) {
      throw new NullPointerException ( "Build must not be null" );
    }
    if ( task == null ) {
      throw new NullPointerException ( "Task must not be null" );
    }
  }

  @Override
  public String getDisplayName () {
    return task.getName ();
  }

  public DistributedProject getProject () {
    return project;
  }

  public DistributedBuild getBuild () {
    return build;
  }

  @Override
  public String getSearchUrl () {
    return null;
  }

  public TaskCoordinator.Task getTask () {
    return task;
  }

  public List<TaskCoordinator.Task> getHistory () {
    List<TaskCoordinator.Task> rt = new ArrayList<TaskCoordinator.Task> ();
    rt.add ( task );
    DistributedBuild t = build.getPreviousBuild ();
    while ( t != null ) {
      final TaskInfo t2 = t.getTask ( task.getName () );
      if ( t2 != null ) {
        rt.add ( t2.getTask () );
      }
      t = t.getPreviousBuild ();
    }
    return rt;
  }

  @CheckForNull
  public Computer getNodeForTask ( TaskCoordinator.Task task ) {
    Computer rt = null;
    DistributedBuild b = project.getBuildByNumber ( task.getBuildnumber () );
    if ( b != null ) {
      DistributedRun run = b.getRuns ().get ( task.getExecutor () );
      if ( run != null ) {
        Node builtOn = run.getBuiltOn ();
        if ( builtOn != null ) {
          rt = builtOn.toComputer ();
        }
      }
    }
    return rt;
  }

}
