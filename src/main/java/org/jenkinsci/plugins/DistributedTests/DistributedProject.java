package org.jenkinsci.plugins.DistributedTests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import antlr.ANTLRException;
import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 * @author David van Laatum
 */
public class DistributedProject extends AbstractProject<DistributedProject, DistributedBuild>
        implements TopLevelItem, SCMTriggerItem, ItemGroup<DistributedTask>,
                   Saveable, Queue.FlyweightTask, BuildableItemWithBuildWrappers {

  private static final Logger LOG
          = Logger.getLogger ( DistributedProject.class.getName () );
  private DescribableList<Builder, Descriptor<Builder>> builders
          = new DescribableList<Builder, Descriptor<Builder>> ( this );
  private DescribableList<Builder, Descriptor<Builder>> setupbuilders
          = new DescribableList<Builder, Descriptor<Builder>> ( this );
  private DescribableList<Builder, Descriptor<Builder>> masteronlybuilders
          = new DescribableList<Builder, Descriptor<Builder>> ( this );
  private DescribableList<Builder, Descriptor<Builder>> postbuilders
          = new DescribableList<Builder, Descriptor<Builder>> ( this );
  private DescribableList<Publisher, Descriptor<Publisher>> publishers
          = new DescribableList<Publisher, Descriptor<Publisher>> ( this );
  private DescribableList<Publisher, Descriptor<Publisher>> subpublishers
          = new DescribableList<Publisher, Descriptor<Publisher>> ( this );
  private DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappers
          = new DescribableList<BuildWrapper, Descriptor<BuildWrapper>> ( this );
  private List<DistributedTask> subTasks = new ArrayList<DistributedTask> ();

  private Label subJobsAssignedLabel;
  private String tasklistfile;
  private Integer executors = 1;

  public DistributedProject ( String name ) {
    this ( Jenkins.getInstance (), name );
  }

  public DistributedProject ( ItemGroup parent, String name ) {
    super ( parent, name );
  }

  public String getTasklistfile () {
    return tasklistfile;
  }

  public void setTaskListFile ( String file ) {
    tasklistfile = file;
  }

  @Override
  public Item asItem () {
    return this;
  }

  @Override
  public AbstractProject<?, ?> asProject () {
    return this;
  }

  @Override
  public TopLevelItemDescriptor getDescriptor () {
    return DESCRIPTOR;
  }

  @Override
  public DistributedTask getItem ( String name ) {
    if ( subTasks.isEmpty () ) {
      resizeSubTasks ();
    }
    return subTasks.get ( Integer.parseInt ( name ) );
  }

  @Exported
  @Override
  public Collection<DistributedTask> getItems () {
    return ImmutableList.copyOf ( subTasks );
  }

  public List<Builder> getBuilders () {
    return builders.toList ();
  }

  public List<Builder> getMasterBuilders () {
    return masteronlybuilders.toList ();
  }

  public DescribableList<Builder, Descriptor<Builder>> getMasterOnlyBuilders () {
    return masteronlybuilders;
  }

  public List<Builder> getSetupBuilders () {
    return setupbuilders.toList ();
  }

  public List<Builder> getPostBuilders () {
    return postbuilders.toList ();
  }

  public DescribableList<Builder, Descriptor<Builder>> getBuildersList () {
    return builders;
  }

  public Map<Descriptor<Publisher>, Publisher> getPublishers () {
    return publishers.toMap ();
  }

  @Override
  public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList () {
    return publishers;
  }

  public Map<Descriptor<Publisher>, Publisher> getSubPublishers () {
    return subpublishers.toMap ();
  }

  public DescribableList<Publisher, Descriptor<Publisher>> getSubPublishersList () {
    return subpublishers;
  }

  @Override
  public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList () {
    return buildWrappers;
  }

  public Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers () {
    return buildWrappers.toMap ();
  }

  public Publisher getPublisher ( Descriptor<Publisher> descriptor ) {
    for ( Publisher p : publishers ) {
      if ( p.getDescriptor () == descriptor ) {
        return p;
      }
    }
    return null;
  }

  @Override
  public File getRootDirFor ( DistributedTask child ) {
    return new File ( this.getRootDir (), child.getName () );
  }

  @Override
  public SCMTrigger getSCMTrigger () {
    return getTrigger ( SCMTrigger.class );
  }

  @Override
  public Collection<? extends SCM> getSCMs () {
    return SCMTriggerItem.SCMTriggerItems.resolveMultiScmIfConfigured (
            getScm () );
  }

  @Override
  public String getUrlChildPrefix () {
    return "item";
  }

  @Override
  public boolean isFingerprintConfigured () {
    return false;
  }

  @Override
  public void onCreatedFromScratch () {
    resizeSubTasks ();
    super.onCreatedFromScratch ();
  }

  @Override
  public void onDeleted ( DistributedTask item ) throws IOException {
    subTasks.remove ( item );
  }

  @Override
  public void onLoad ( ItemGroup<? extends Item> parent, String name ) throws
          IOException {
    super.onLoad ( parent, name );
    Integer i = 0;
    if ( subTasks != null ) {
      for ( DistributedTask t : subTasks ) {
        try {
          t.setNumber ( i );
          t.onLoad ( this, t.getName () );
        } catch ( NullPointerException ex ) {
          LOG.log ( Level.SEVERE, null, ex );
        }
        i++;
      }
    }
    resizeSubTasks ();
  }

  @Override
  public void onRenamed ( DistributedTask item, String oldName, String newName )
          throws IOException {

  }

  @Override
  public void logRotate () throws IOException, InterruptedException {
    super.logRotate ();
    for ( DistributedTask t : subTasks ) {
      t.logRotate ();
    }
  }

  @Override
  public String getPronoun () {
    return "Task";
  }

  @Override
  public QueueTaskFuture<?> scheduleBuild2 ( int quietPeriod, Action... actions ) {
    return scheduleBuild2 ( quietPeriod, null, actions );
  }

  @Override
  protected Class<DistributedBuild> getBuildClass () {
    return DistributedBuild.class;
  }

  public String getSubJobsAssignedLabelString () {
    if ( subJobsAssignedLabel != null ) {
      return subJobsAssignedLabel.toString ();
    }
    return null;
  }

  public void setSubJobsAssignedLabelString ( String value ) throws
          ANTLRException {
    if ( value != null && !value.isEmpty () ) {
      subJobsAssignedLabel = Label.parseExpression ( value );
    } else {
      subJobsAssignedLabel = null;
    }
  }

  public Label getSubJobsAssignedLabel () {
    return subJobsAssignedLabel;
  }

  public void setSubJobsAssignedLabel ( Label value ) {
    subJobsAssignedLabel = value;
  }

  @Override
  protected void submit ( StaplerRequest req, StaplerResponse rsp ) throws
          IOException, ServletException, Descriptor.FormException {
    super.submit ( req, rsp );
    JSONObject json = req.getSubmittedForm ();

    if ( req.getParameter ( "hasSlaveAffinity" ) != null ) {
      try {
        setSubJobsAssignedLabelString ( Util.fixEmptyAndTrim ( req
                .getParameter ( "_.subJobsAssignedLabelString" ) ) );
      } catch ( ANTLRException ex ) {
        LOG.log ( Level.SEVERE, null, ex );
      }
    } else {
      subJobsAssignedLabel = null;
    }

    executors = Integer.parseInt ( req.getParameter ( "executors" ) );
    if ( executors < 1 ) {
      executors = 1;
    }
    tasklistfile = req.getParameter ( "tasklistfile" );

    buildWrappers.rebuild ( req, json, BuildWrappers.getFor ( this ) );
    setupbuilders.rebuildHetero ( req, json, Builder.all (), "setup" );
    masteronlybuilders.rebuildHetero ( req, json, Builder.all (),
                                       "mastersetup" );
    builders.rebuildHetero ( req, json, Builder.all (), "run" );
    postbuilders.rebuildHetero ( req, json, Builder.all (), "post" );
    publishers.rebuildHetero ( req, json, Publisher.all (), "publisher" );
    subpublishers.rebuildHetero ( req, json, Publisher.all (), "subpublisher" );
    resizeSubTasks ();
  }

  protected void resizeSubTasks () {
    if ( executors == null || executors < 1 ) {
      executors = 1;
    }
    if ( subTasks == null ) {
      subTasks = new ArrayList<DistributedTask> ( executors );
    }
    while ( subTasks.size () < executors ) {
      DistributedTask t = new DistributedTask ( this, subTasks.size () );
      subTasks.add ( t );
      t.onCreatedFromScratch ();
    }
    if ( subTasks.size () > executors ) {
      for ( Iterator<DistributedTask> t = subTasks.iterator (); t.hasNext (); ) {
        DistributedTask next = t.next ();
        if ( next.getLastBuild () == null ) {
          t.remove ();
        }
        if ( subTasks.size () <= executors ) {
          break;
        }
      }
    }
    Integer i = 0;
    for ( DistributedTask t : subTasks ) {
      try {
        t.setNumber ( i );
        t.renameTo ( i.toString () );
      } catch ( IOException ex ) {
        LOG.log ( Level.SEVERE, null, ex );
      }
      i++;
    }
    try {
      save ();
    } catch ( IOException ex ) {
      LOG.log ( Level.SEVERE, null, ex );
    }
  }

  public Integer getExecutors () {
    return executors;
  }

  @Override
  public ContextMenu doChildrenContextMenu ( StaplerRequest request,
                                             StaplerResponse response ) throws
          Exception {
    ContextMenu menu = super.doChildrenContextMenu ( request, response );

    for ( DistributedTask t : getItems () ) {
      menu.add ( t );
    }

    return menu;
  }

  public Object readResolve () {
    if ( subpublishers == null ) {
      subpublishers = new DescribableList<Publisher, Descriptor<Publisher>> (
              this );
    }
    return this;
  }

  @Override
  protected List<Action> createTransientActions () {
    List<Action> r = super.createTransientActions ();

    for ( BuildStep step : builders ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( BuildStep step : masteronlybuilders ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( BuildStep step : postbuilders ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( BuildStep step : publishers ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( BuildStep step : subpublishers ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( BuildWrapper step : buildWrappers ) {
      r.addAll ( step.getProjectActions ( this ) );
    }
    for ( Trigger trigger : triggers () ) {
      r.addAll ( trigger.getProjectActions () );
    }

    return r;
  }

  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl ();
  public static final String DESCRIPTION = "Distributed Tests";

  public static final class DescriptorImpl extends AbstractProjectDescriptor {

    @Override
    public String getDisplayName () {
      return DESCRIPTION;
    }

    @Override
    public DistributedProject newInstance ( ItemGroup parent, String name ) {
      return new DistributedProject ( parent, name );
    }

    @Override
    public boolean isApplicable ( Descriptor descriptor ) {
      return true;
    }

  }

}
