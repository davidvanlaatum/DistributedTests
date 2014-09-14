package org.jenkinsci.plugins.DistributedTests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableList;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.ScheduleResult;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionSCM;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import static hudson.model.BallColor.NOTBUILT;
import static hudson.model.Result.SUCCESS;

/**
 *
 * @author David van Laatum
 */
public class DistributedBuild extends AbstractBuild<DistributedProject, DistributedBuild>
        implements ModelObjectWithChildren {

  private static final Logger LOG
          = Logger.getLogger ( DistributedBuild.class.getName () );
  private static final Jenkins JENKINS = Jenkins.getInstance ();

  private TaskCoordinator coordinator = new TaskCoordinator ( this );
  private transient List<DistributedRun> runs = new ArrayList<DistributedRun> ();

  public DistributedBuild ( DistributedProject job ) throws IOException {
    super ( job );
  }

  public DistributedBuild ( DistributedProject job, Calendar timestamp ) {
    super ( job, timestamp );
  }

  public DistributedBuild ( DistributedProject project, File buildDir ) throws
          IOException {
    super ( project, buildDir );
  }

  @Override
  public void delete () throws IOException {
    for ( DistributedRun r : runs ) {
      r.delete ();
    }
    super.delete ();
  }

  @Override
  public synchronized void deleteArtifacts () throws IOException {
    for ( DistributedRun r : runs ) {
      r.deleteArtifacts ();
    }
    super.deleteArtifacts ();
  }

  @Override
  public ContextMenu doChildrenContextMenu ( StaplerRequest request,
                                             StaplerResponse response ) throws
          Exception {
    ContextMenu menu = new ContextMenu ();

    int index = 0;
    for ( DistributedRun t : runs ) {
      menu.add ( new MenuItem ().withDisplayName ( t.getFullDisplayName () )
              .withIcon ( t.getResult () != null ? t.getResult ().color
                                  : NOTBUILT )
              .withContextRelativeUrl ( t.getUrl () ) );
    }

    return menu;
  }

  public DistributedRun getRun ( String id ) {
    return runs.get ( Integer.parseInt ( id ) );
  }

  @Exported
  public List<DistributedRun> getRuns () {
    return ImmutableList.copyOf ( runs );
  }

  public List<TaskCoordinator.Task> getTasks () {
    TaskCoordinator co = getAction ( TaskCoordinator.class );
    if ( co != null ) {
      return co.getTasks ();
    } else {
      return null;
    }
  }

  public TaskInfo getTask ( String name ) {
    TaskCoordinator co = getAction ( TaskCoordinator.class );
    if ( co != null ) {
      TaskCoordinator.Task t = co.getTask ( name );
      if ( t != null ) {
        return new TaskInfo ( getProject (), this, t );
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  protected void onLoad () {
    super.onLoad ();
    runs = new ArrayList<DistributedRun> ();
    for ( DistributedTask task : getParent ().getItems () ) {
      DistributedRun buildByNumber = task.getBuildByNumber ( getNumber () );
      if ( buildByNumber != null ) {
        runs.add ( buildByNumber );
      }
    }
  }

  @Override
  public void run () {
    execute ( new DistributedExecutor () );
  }

  public class DistributedExecutor extends AbstractBuildExecution {

    public String getDisplayName () {
      return null;
    }

    public String getIconFileName () {
      return null;
    }

    public String getUrlName () {
      return null;
    }

    @Override
    protected Result doRun ( BuildListener listener ) throws Exception,
                                                             RunnerAbortedException {
      Result rt = SUCCESS;
      Exception e = null;
      Queue queue = JENKINS.getQueue ();
      List<ScheduleResult> results
              = new ArrayList<ScheduleResult> ( getProject ().getExecutors () );
      Collection<DistributedTask> subtasks = getProject ().getItems ();

      if ( getProject ().getTasklistfile () == null || getProject ()
              .getTasklistfile ().isEmpty () ) {
        throw new IllegalArgumentException ( "Task list file is not set!" );
      }

      coordinator.setListener ( listener );
      addAction ( coordinator );

      if ( build ( listener, getProject ().getSetupBuilders () ) ) {
        listener.getLogger ().println (
                "Setup complete starting master only steps" );

        if ( build ( listener, getProject ().getMasterBuilders () ) ) {
          InputStream tasks = getWorkspace ().child ( getProject ()
                  .getTasklistfile () ).read ();
          try {
            coordinator.readTasks ( tasks );
          } finally {
            if ( tasks != null ) {
              tasks.close ();
            }
          }

          listener.getLogger ().println ( "Starting executors" );

          try {
            Action revisionaction = null;
            if ( getChangeSet () instanceof SubversionChangeLogSet ) {
              List<SubversionSCM.SvnInfo> revs
                      = new ArrayList<SubversionSCM.SvnInfo> ();
              for ( Map.Entry<String, Long> rev
                            : ( (SubversionChangeLogSet) getChangeSet () )
                      .getRevisionMap ().entrySet () ) {
                revs.add ( new SubversionSCM.SvnInfo ( rev.getKey (),
                                                       rev.getValue () ) );
              }
              revisionaction = new RevisionParameterAction ( revs );
            } else {
              listener.error ( "Unsupported SCM, workspaces may not match!" );
            }
            ParametersAction parameters = getAction ( ParametersAction.class );
            for ( DistributedTask t : subtasks ) {
              List<Action> actions = new ArrayList<Action> ();
              actions.add ( coordinator );
              if ( parameters != null ) {
                actions.add ( new ParametersAction ( parameters
                        .getParameters () ) );
              }
              if ( revisionaction != null ) {
                actions.add ( revisionaction );
              }
              ScheduleResult sc = queue.schedule2 ( t, 0, actions );
              if ( sc.isRefused () ) {
                for ( ScheduleResult sc2 : results ) {
                  sc2.getItem ().getFuture ().cancel ( false );
                }
                throw new IllegalStateException ( "Failed to queue task" );
              }
              results.add ( sc );
              if ( results.size () >= getProject ().getExecutors () ) {
                break;
              }
            }

            for ( ScheduleResult sc : results ) {
              sc.getItem ().getFuture ().waitForStart ();
            }
          } catch ( IllegalStateException ex ) {
            listener.getLogger ().println ( ex );
            e = ex;
          }

          for ( DistributedTask t : subtasks ) {
            DistributedRun lastBuild = t.getBuildByNumber ( getNumber () );
            if ( lastBuild != null ) {
              runs.add ( lastBuild );
            }
          }

          save ();

          @SuppressWarnings ( "unchecked" )
          List<DistributedRun> localruns = new ArrayList ( runs );

          boolean done = false;
          while ( !done ) {
            Thread.sleep ( 1000 );
            done = true;
            for ( Iterator<DistributedRun> r = localruns.iterator (); r
                  .hasNext (); ) {
              DistributedRun lastBuild = r.next ();
              if ( lastBuild.isBuilding () ) {
                done = false;
              } else {
                r.remove ();
                if ( lastBuild.getResult () != SUCCESS ) {
                  rt = lastBuild.getResult ();
                }
                listener.hyperlink ( "/" + lastBuild.getUrl (), lastBuild
                                     .getProject ().getDisplayName () );
                listener.getLogger ().print ( " " );
                listener.getLogger ().println ( lastBuild.getResult () );
              }
            }
          }

          if ( e != null ) {
            throw e;
          }

          listener.getLogger ()
                  .println ( "Executors complete running post builders" );
          if ( !build ( listener, getProject ().getPostBuilders () ) ) {
            listener.error ( "Post builders failed" );
            rt = Result.FAILURE;
          }
        } else {
          listener.error ( "Master builders failed" );
          rt = Result.FAILURE;
        }
      } else {
        listener.error ( "Setup failed" );
        rt = Result.FAILURE;
      }

      getProject ().resizeSubTasks ();

      return rt;
    }

    @Override
    protected void post2 ( BuildListener listener ) throws Exception {
      for ( Publisher p : getProject ().getPublishersList () ) {
        p.perform ( DistributedBuild.this, launcher, listener );
      }
    }

    protected boolean build ( @Nonnull BuildListener listener,
                              @Nonnull Collection<Builder> steps ) throws
            IOException, InterruptedException {
      for ( BuildStep bs : steps ) {
        if ( !perform ( bs, listener ) ) {
          LOG.log ( Level.FINE, "{0} : {1} failed", new Object[]{
            DistributedBuild.this, bs } );
          return false;
        }

        Executor executor = getExecutor ();
        if ( executor != null && executor.isInterrupted () ) {
          // someone asked build interruption, let stop the build before trying to run another build step
          throw new InterruptedException ();
        }
      }
      return true;
    }

    @Override
    public void cleanUp ( BuildListener listener ) throws Exception {
      if ( coordinator != null ) {
        coordinator.cleanUp ();
      }
      super.cleanUp ( listener );
    }

  }

}
