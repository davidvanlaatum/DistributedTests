package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.jenkinsci.plugins.DistributedTests.TaskCoordinator.Task;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.powermock.api.mockito.PowerMockito;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;

/**
 * @author David van Laatum
 */
public class TaskCoordinatorTest {

  @Rule
  public JenkinsRule j = new JenkinsRule ();

  @Test
  public void testRead () throws IOException, Exception {
    DistributedProject project = new DistributedProject ( j.jenkins, "Test" );
    project.onCreatedFromScratch ();
    project.setTaskListFile ( "test.json" );

    TestBuilder builder1 = new TestBuilder () {
      @Override
      public boolean perform ( AbstractBuild<?, ?> build,
                               Launcher launcher,
                               BuildListener listener ) throws
              InterruptedException, IOException {
        build.getWorkspace ().child ( "test.json" )
                .copyFrom ( getClass ().getResource ( "test.json" ) );
        return true;
      }
    };

    TestBuilder builder2 = new TestBuilder () {
      @Override
      public boolean perform ( AbstractBuild<?, ?> build,
                               Launcher launcher,
                               BuildListener listener ) throws
              InterruptedException, IOException {
        Thread.sleep ( 1000 );
        return true;
      }
    };

    project.getMasterOnlyBuilders ().add ( builder1 );
    project.getBuildersList ().add ( builder2 );

    DistributedBuild build = project.scheduleBuild2 ( 0 ).get ();
    assertEquals ( build.getLog ( 100 ).toString (), Result.SUCCESS, build
                   .getResult () );

    assertEquals ( 2, build.getTasks ().size () );
    assertEquals ( "Task1", build.getTasks ().get ( 0 ).getName () );
    assertEquals ( "Task2", build.getTasks ().get ( 1 ).getName () );
    Assert.assertThat ( build.getTasks ().get ( 0 ).getDuration (),
                        greaterThanOrEqualTo ( 1000l ) );
    Assert.assertThat ( build.getTasks ().get ( 1 ).getDuration (),
                        greaterThanOrEqualTo ( 1000l ) );
  }

  @Test
  public void testGetDuration () {
    Task t = PowerMockito.mock ( Task.class );
    PowerMockito.when ( t.getDuration () ).thenReturn ( 1000l, 61000l, 3661000l,
                                                        60000l, 3600000l, 1l, 0l );
    PowerMockito.when ( t.getDurationstring () ).thenCallRealMethod ();
    assertEquals ( "1s", t.getDurationstring () );
    assertEquals ( "1m1s", t.getDurationstring () );
    assertEquals ( "1h1m1s", t.getDurationstring () );
    assertEquals ( "1m", t.getDurationstring () );
    assertEquals ( "1h", t.getDurationstring () );
    assertEquals ( "1ms", t.getDurationstring () );
    assertEquals ( "0ms", t.getDurationstring () );
  }

  @Test
  public void testGetDurationDiff () {
    Task t = PowerMockito.mock ( Task.class );
    PowerMockito.when ( t.getDurationDiff () )
            .thenReturn ( 1000l, 61000l, 3661000l, 60000l, 3600000l, 1l, 0l,
                          - 1000l, -61000l, -3661000l, -60000l, -3600000l, -1l );
    PowerMockito.when ( t.getDurationDiffString () ).thenCallRealMethod ();
    assertEquals ( "+1s", t.getDurationDiffString () );
    assertEquals ( "+1m1s", t.getDurationDiffString () );
    assertEquals ( "+1h1m1s", t.getDurationDiffString () );
    assertEquals ( "+1m", t.getDurationDiffString () );
    assertEquals ( "+1h", t.getDurationDiffString () );
    assertEquals ( "+1ms", t.getDurationDiffString () );
    assertEquals ( "+0ms", t.getDurationDiffString () );

    assertEquals ( "-1s", t.getDurationDiffString () );
    assertEquals ( "-1m1s", t.getDurationDiffString () );
    assertEquals ( "-1h1m1s", t.getDurationDiffString () );
    assertEquals ( "-1m", t.getDurationDiffString () );
    assertEquals ( "-1h", t.getDurationDiffString () );
    assertEquals ( "-1ms", t.getDurationDiffString () );
  }

}
