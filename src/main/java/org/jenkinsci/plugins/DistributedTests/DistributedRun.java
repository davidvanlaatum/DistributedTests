package org.jenkinsci.plugins.DistributedTests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.scm.SubversionSCM;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import javax.annotation.Nonnull;
import static hudson.model.Result.FAILURE;
import static org.jenkinsci.plugins.DistributedTests.BuildStage.*;

/**
 * @author David van Laatum
 */
public class DistributedRun extends Build<DistributedTask, DistributedRun>
        implements Action {

  protected TaskCoordinator.Task currentTask;
  private BuildStage stage;
  protected transient DistributedBuild parentBuild;

  public DistributedRun ( DistributedTask job ) throws IOException {
    super ( job );
  }

  public DistributedRun ( DistributedTask job, Calendar timestamp ) {
    super ( job, timestamp );
  }

  public DistributedRun ( DistributedTask project, File buildDir ) throws
          IOException {
    super ( project, buildDir );
  }

  public Object readResolve () {
    if ( this.stage == null ) {
      this.stage = Complete;
    }
    return this;
  }

  public BuildStage getStage () {
    return stage;
  }

  public TaskCoordinator.Task getCurrentTask () {
    return currentTask;
  }

  @Override
  public Map<String, String> getBuildVariables () {
    Map<String, String> buildVariables = super.getBuildVariables ();
    if ( currentTask != null ) {
      buildVariables.put ( "TASK", currentTask.getName () );
    }
    return buildVariables;
  }

  @Override
  public String getIconFileName () {
    return " ";
  }

  @Override
  public String getUrlName () {
    return this.getId ();
  }

  @Override
  public void run () {
    execute ( new DistributedExecutor () );
  }

  @Override
  public AbstractBuild<?, ?> getRootBuild () {
    return parentBuild;
  }

  protected class DistributedExecutor extends BuildExecution {

    @Override
    public void defaultCheckout () throws IOException, InterruptedException {
      hudson.scm.RevisionParameterAction svnrevs
              = getAction ( hudson.scm.RevisionParameterAction.class );
      if ( svnrevs != null ) {
        listener.getLogger ().println ( "Revisions locked to:" );
        for ( SubversionSCM.SvnInfo i : svnrevs.getRevisions () ) {
          listener.getLogger ().println ( i.url + "@" + Long.toString (
                  i.revision ) );
        }
      }
      stage = Checkout;
      super.defaultCheckout ();
    }

    @Override
    protected Result doRun ( @Nonnull BuildListener listener ) throws Exception {
      stage = Setup;
      if ( !preBuild ( listener, project.getBuilders () ) ) {
        return FAILURE;
      }

      Result r = null;
      try {
        List<BuildWrapper> wrappers = new ArrayList<BuildWrapper> ( project
                .getBuildWrappers ().values () );

        ParametersAction parameters = getAction ( ParametersAction.class );
        if ( parameters != null ) {
          parameters.createBuildWrappers ( DistributedRun.this, wrappers );
        }

        for ( BuildWrapper w : wrappers ) {
          Environment e = w.setUp ( (AbstractBuild<?, ?>) DistributedRun.this,
                                    launcher, listener );
          if ( e == null ) {
            return FAILURE;
          }
          buildEnvironments.add ( e );
        }

        List<Builder> builders = new ArrayList<Builder> ();
        List<Builder> copiers = new ArrayList<Builder> ();

        for ( Builder b : project.getBuilders () ) {
          if ( b instanceof DistributedSync ) {
            copiers.add ( b );
          } else {
            builders.add ( b );
          }
        }

        TaskCoordinator coord = getAction ( TaskCoordinator.class );
        parentBuild = coord.getBuild ();

        if ( build ( listener, project.getParent ().getSetupBuilders () ) ) {
          stage = Running;
          currentTask = coord.getNextTask ( _this () );
          while ( currentTask != null ) {
            boolean success = build ( listener, builders );
            if ( !success ) {
              r = FAILURE;
            }
            currentTask.complete ( success ? Result.SUCCESS : Result.FAILURE );
            currentTask = coord.getNextTask ( _this () );
          }
          stage = SyncingFiles;
          if ( !build ( listener, copiers ) ) {
            r = FAILURE;
          }
        }
      } catch ( InterruptedException e ) {
        r = Executor.currentExecutor ().abortResult ();
        // not calling Executor.recordCauseOfInterruption here. We do that where this exception is consumed.
        throw e;
      } finally {
        if ( r != null ) {
          setResult ( r );
        }
        // tear down in reverse order
        boolean failed = false;
        for ( int i = buildEnvironments.size () - 1; i >= 0; i-- ) {
          if ( !buildEnvironments.get ( i ).tearDown ( DistributedRun.this,
                                                       listener ) ) {
            failed = true;
          }
        }
        // WARNING The return in the finally clause will trump any return before
        if ( failed ) {
          r = FAILURE;
        }
      }

      stage = Post;
      return r;
    }

    protected boolean build ( @Nonnull BuildListener listener,
                              @Nonnull Collection<Builder> steps ) throws
            IOException, InterruptedException {
      for ( BuildStep bs : steps ) {
        if ( !perform ( bs, listener ) ) {
          LOGGER.log ( Level.FINE, "{0} : {1} failed", new Object[]{
            DistributedRun.this, bs } );
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
    protected WorkspaceList.Lease decideWorkspace ( Node n, WorkspaceList wsl )
            throws InterruptedException, IOException {
      return wsl.allocate ( n.getWorkspaceFor ( getParent ().getParent () ) );
    }

    @Override
    public void cleanUp ( BuildListener listener ) throws Exception {
      stage = Complete;
      super.cleanUp ( listener );
    }

  }
  private static final Logger LOGGER
          = Logger.getLogger ( DistributedRun.class.getName () );

}
