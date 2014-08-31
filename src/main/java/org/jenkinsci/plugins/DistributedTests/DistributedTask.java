package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Project;
import hudson.model.Queue.NonBlockingTask;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import jenkins.triggers.SCMTriggerItem;

/**
 * @author David van Laatum
 */
public class DistributedTask extends Project<DistributedTask, DistributedRun>
        implements SCMTriggerItem, NonBlockingTask {

  private static final Logger LOG
          = Logger.getLogger ( DistributedTask.class.getName () );

  private Integer number;

  public DistributedTask ( DistributedProject parent, Integer number ) {
    super ( parent, number.toString () );
    this.number = number;
    try {
      this.setAssignedLabel ( parent.getSubJobsAssignedLabel () );
    } catch ( IOException ex ) {
      LOG.log ( Level.SEVERE, null, ex );
    }
  }

  @Override
  public String getDisplayName () {
    return "Executor " + Integer.toString ( number + 1 );
  }

  @Override
  public void onLoad ( ItemGroup<? extends Item> parent, String name ) throws
          IOException {
    if ( number == null ) {
      number = 0;
    }
    // directory name is not a name for us --- it's taken from the combination name
    super.onLoad ( parent, number.toString () );
  }

  @Override
  public synchronized int assignBuildNumber () throws IOException {
    return getParent ().getLastBuild ().getNumber ();
  }

  @Override
  public Label getAssignedLabel () {
    return getParent ().getSubJobsAssignedLabel ();
  }

  @Override
  protected Class<DistributedRun> getBuildClass () {
    return DistributedRun.class;
  }

  @Override
  public boolean isConfigurable () {
    return false;
  }

  @Override
  public List<Builder> getBuilders () {
    return getParent ().getBuilders ();
  }

  @Override
  public DescribableList<Builder, Descriptor<Builder>> getBuildersList () {
    return getParent ().getBuildersList ();
  }

  @Override
  public Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers () {
    return getParent ().getBuildWrappers ();
  }

  @Override
  public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList () {
    return getParent ().getBuildWrappersList ();
  }

  @Override
  public SCM getScm () {
    return getParent ().getScm ();
  }

  @Override
  public Collection<? extends SCM> getSCMs () {
    return getParent ().getSCMs ();
  }

  @Override
  public DistributedProject getParent () {
    return (DistributedProject) super.getParent ();
  }

  void setNumber ( Integer i ) {
    number = i;
  }

}
