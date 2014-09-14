package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableList;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;
import static hudson.model.Result.NOT_BUILT;

/**
 * @author David van Laatum
 */
public class TaskCoordinator extends InvisibleAction {

  private static final Logger LOG
          = Logger.getLogger ( TaskCoordinator.class.getName () );
  private transient final DistributedBuild build;
  private transient BuildListener listener;

  private transient Queue<Task> taskQueue;
  private List<Task> tasks;
  private SortedMap<String, Task> tasklist = new TreeMap<String, Task> ();

  TaskCoordinator ( DistributedBuild build ) {
    this.build = build;
  }

  public void readTasks ( InputStream source ) throws IOException {
    StringWriter writer = new StringWriter ();
    IOUtils.copy ( source, writer );
    JSONArray a = (JSONArray) JSONSerializer.toJSON ( writer.toString () );
    for ( Object v : a ) {
      Task t = new Task ( (JSONObject) v );
      t.buildnumber = build.getNumber ();
      tasklist.put ( t.getName (), t );
    }

    if ( listener != null ) {
      listener.getLogger ().println ( "Read " + tasklist.size () + " tasks" );
    }

    TaskCoordinator tc = findPrevious ();
    if ( tc != null ) {
      for ( Task t : tasklist.values () ) {
        Task t2 = tc.getTask ( t.getName () );
        if ( t2 != null ) {
          t.lastDuration = t2.getDuration ();
        }
      }
    }
    taskQueue = new PriorityQueue<Task> ();
    for ( Task t : tasklist.values () ) {
      taskQueue.add ( t );
    }
  }

  protected TaskCoordinator findPrevious () {
    TaskCoordinator tc = null;
    DistributedBuild last = build.getPreviousNotFailedBuild ();
    if ( last == null ) {
      last = build.getPreviousCompletedBuild ();
    }
    if ( last != null ) {
      tc = last.getAction ( TaskCoordinator.class );
      if ( tc != null ) {
        listener.getLogger ().println ( "Using Tasks from build " + last
                .getNumber () + " for comparision" );
      }
    }
    if ( tc == null ) {
      listener.getLogger ().println (
              "Failed to find previous build to use for comparision" );
    }
    return tc;
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
      t.executor = run.getProject ().getNumber ();
      t.started = new Date ();
      t.running = true;
    }
    return t;
  }

  public List<Task> getTasks () {
    return ImmutableList.copyOf ( tasklist.values () );
  }

  public Object readResolve () {
    if ( tasks != null ) {
      tasklist = new TreeMap<String, Task> ();
      for ( Task t : tasks ) {
        tasklist.put ( t.getName (), t );
      }
      tasks.clear ();
      tasks = null;
    }
    return this;
  }

  public Task getTask ( String name ) {
    return tasklist.get ( name );
  }

  void setListener ( BuildListener listener ) {
    this.listener = listener;
  }

  public void cleanUp () {
    for ( Task t : tasklist.values () ) {
      t.running = false;
    }
  }

  public class Task implements Comparable<Task> {

    private final String name;
    private final SortedMap<String, String> env = new TreeMap<String, String> ();
    private Boolean run = false;
    private Boolean running = false;
    private Date started;
    private Date finished;
    private Integer executor;
    private Result result = NOT_BUILT;
    private Long lastDuration;
    private Integer buildnumber;

    @SuppressWarnings ( "unchecked" )
    public Task ( JSONObject data ) {
      name = data.getString ( "name" );
      for ( Object v : data.getJSONObject ( "env" ).entrySet () ) {
        env.put ( ( (Map.Entry<String, String>) v ).getKey (),
                  ( (Map.Entry<String, String>) v ).getValue () );
      }
    }

    @Override
    public int compareTo ( Task o ) {
      if ( o == null || lastDuration == null || o.lastDuration == null ) {
        return 0;
      } else if ( lastDuration > o.lastDuration ) {
        return -1;
      } else if ( lastDuration < o.lastDuration ) {
        return 1;
      } else {
        return 0;
      }
    }

    public String getName () {
      return name;
    }

    public Integer getExecutor () {
      return executor;
    }

    public Date getFinished () {
      return finished;
    }

    public Date getStarted () {
      return started;
    }

    public Boolean hasRun () {
      return run;
    }

    public Boolean isRunning () {
      return running;
    }

    public Result getResult () {
      return result;
    }

    public Integer getBuildnumber () {
      return buildnumber;
    }

    public Long getDuration () {
      Long duration = null;

      if ( finished != null && started != null ) {
        duration = finished.getTime () - started.getTime ();
      } else if ( started != null ) {
        duration = new Date ().getTime () - started.getTime ();
      }

      return duration;
    }

    public String getDurationstring () {
      StringBuilder sb = new StringBuilder ();
      Long duration = getDuration ();
      if ( duration != null ) {
        long diffInHours = TimeUnit.MILLISECONDS.toHours ( duration );
        if ( diffInHours > 0 ) {
          sb.append ( diffInHours ).append ( "h" );
          duration -= TimeUnit.HOURS.toMillis ( diffInHours );
        }
        long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes ( duration );
        if ( diffInMinutes > 0 ) {
          sb.append ( diffInMinutes ).append ( "m" );
          duration -= TimeUnit.MINUTES.toMillis ( diffInMinutes );
        }
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds ( duration );
        if ( diffInSeconds > 0 ) {
          sb.append ( diffInSeconds ).append ( "s" );
          duration -= TimeUnit.SECONDS.toMillis ( diffInSeconds );
        } else if ( sb.length () == 0 ) {
          sb.append ( duration ).append ( "ms" );
        }
      }
      return sb.toString ();
    }

    public Long getDurationDiff () {
      Long duration = getDuration ();
      if ( lastDuration == null || duration == null ) {
        return null;
      }
      return getDuration () - lastDuration;
    }

    public String getDurationDiffString () {
      StringBuilder sb = new StringBuilder ();
      Long duration = getDurationDiff ();
      if ( duration != null ) {
        if ( duration < 0 ) {
          duration *= -1;
          sb.append ( "-" );
        } else {
          sb.append ( "+" );
        }
        long diffInHours = TimeUnit.MILLISECONDS.toHours ( duration );
        if ( diffInHours > 0 ) {
          sb.append ( diffInHours ).append ( "h" );
          duration -= TimeUnit.HOURS.toMillis ( diffInHours );
        }
        long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes ( duration );
        if ( diffInMinutes > 0 ) {
          sb.append ( diffInMinutes ).append ( "m" );
          duration -= TimeUnit.MINUTES.toMillis ( diffInMinutes );
        }
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds ( duration );
        if ( diffInSeconds > 0 ) {
          sb.append ( diffInSeconds ).append ( "s" );
          duration -= TimeUnit.SECONDS.toMillis ( diffInSeconds );
        } else if ( sb.length () <= 1 ) {
          sb.append ( duration ).append ( "ms" );
        }
      }
      return sb.toString ();
    }

    public Map<String, String> getEnv () {
      return env;
    }

    public void complete ( Result status ) {
      listener.getLogger ().println ( name + " completed" );
      finished = new Date ();
      result = status;
      running = false;
      run = true;
    }

    public Object readResolve () {
      if ( run == null ) {
        run = true;
      }
      if ( running == null || running ) {
        running = false;
      }
      if ( executor == null ) {
        executor = 0;
      }
      if ( started == null ) {
        started = new Date ();
      }
      if ( finished == null ) {
        finished = new Date ();
      }
      return this;
    }
  }

}
