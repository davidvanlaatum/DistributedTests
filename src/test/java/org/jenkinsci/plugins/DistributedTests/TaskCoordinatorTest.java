package org.jenkinsci.plugins.DistributedTests;

import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

/**
 * @author David van Laatum
 */
public class TaskCoordinatorTest {

  @Test
  public void testRead () throws IOException {
    TaskCoordinator test = new TaskCoordinator ( null );

    InputStream source = getClass ().getResourceAsStream ( "test.json" );
    try {
      test.readTasks ( source );
    } finally {
      if ( source != null ) {
        source.close ();
      }
    }

  }

}
