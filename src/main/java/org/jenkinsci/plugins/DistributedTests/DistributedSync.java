package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import java.util.logging.Logger;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author David van Laatum
 */
public class DistributedSync extends Builder {

  private static final Jenkins JENKINS = Jenkins.getInstance ();
  private static final Logger LOG
          = Logger.getLogger ( DistributedSync.class.getName () );
  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl ();
  public static final String BUILDER_DISPLAYNAME
          = "Syncronize Files To Master";
  private String filePattern;

  @DataBoundConstructor
  public DistributedSync () {
  }

  @Override
  public BuildStepDescriptor<Builder> getDescriptor () {
    return DESCRIPTOR;
  }

  @Override
  public boolean perform ( AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener ) throws InterruptedException,
                                                           IOException {
    FilePath dest = build.getRootBuild ().getWorkspace ();
    FilePath local = build.getWorkspace ();
    int count = local.copyRecursiveTo ( filePattern, dest );
    listener.getLogger ().println ( "Copied " + count + " files" );
    return true;
  }

  @DataBoundSetter
  public void setFilePattern ( String pattern ) {
    this.filePattern = pattern;
  }

  public String getFilePattern () {
    return this.filePattern;
  }

  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Override
    public String getDisplayName () {
      return BUILDER_DISPLAYNAME;
    }

    @Override
    public boolean isApplicable ( Class<? extends AbstractProject> jobType ) {
      return DistributedTask.class.isAssignableFrom ( jobType );
    }

    @Override
    public boolean configure ( StaplerRequest req, JSONObject json ) throws
            FormException {
      req.bindJSON ( this, json );
      return super.configure ( req, json );
    }
  }
}
