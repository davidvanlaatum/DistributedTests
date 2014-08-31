package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;

/**
 * @author David van Laatum
 */
public class TaskCoordinator extends InvisibleAction {

  private static final Logger LOG
          = Logger.getLogger ( TaskCoordinator.class.getName () );
  private final DistributedBuild build;
  private transient BuildListener listener;

  private Queue<Task> taskQueue = new LinkedList<Task> ();
  private List<Task> tasks = new ArrayList<Task> ();

  TaskCoordinator ( DistributedBuild build ) {
    this.build = build;
  }

  public void readTasks ( InputStream source ) throws IOException {
    StringWriter writer = new StringWriter ();
    IOUtils.copy ( source, writer );
    JSONArray a = (JSONArray) JSONSerializer.toJSON ( writer.toString () );
    for ( Object v : a ) {
      Task t = new Task ( (JSONObject) v );
      tasks.add ( t );
      taskQueue.add ( t );
    }

    if ( listener != null ) {
      listener.getLogger ().println ( "Read " + tasks.size () + " tasks" );
    }
  }

  public DistributedBuild getBuild () {
    return build;
  }

  public synchronized Task getNextTask ( DistributedRun run ) {
    Task t = taskQueue.poll ();
    if ( t != null ) {
      listener.getLogger ()
              .println ( "Allocating " + t.getName () + " to "
                                 + run.getProject ().getDisplayName () );
    }
    return t;
  }

  public Object readResolve () {
    return this;
  }

  void setListener ( BuildListener listener ) {
    this.listener = listener;
  }

  public class Task {

    private final String name;
    private final SortedMap<String, String> env = new TreeMap<String, String> ();

    @SuppressWarnings ( "unchecked" )
    public Task ( JSONObject data ) {
      name = data.getString ( "name" );
      for ( Object v : data.getJSONObject ( "env" ).entrySet () ) {
        env.put ( ( (Map.Entry<String, String>) v ).getKey (),
                  ( (Map.Entry<String, String>) v ).getValue () );
      }
    }

    public String getName () {
      return name;
    }

    public Map<String, String> getEnv () {
      return env;
    }

    public void complete () {
      listener.getLogger ().println ( name + " completed" );
    }
  }

}
