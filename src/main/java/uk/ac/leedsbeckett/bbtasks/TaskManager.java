/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import uk.ac.leedsbeckett.bbtasks.tasks.BaseTask;

/**
 *
 * @author jon
 */
public class TaskManager
{
  WebAppCore core;
  ScheduledThreadPoolExecutor taskrunner  = new ScheduledThreadPoolExecutor( 1 );
  ArrayList<ScheduledTaskInfo> scheduledtasks = new ArrayList<>();

  public TaskManager( WebAppCore core )
  {
    this.core = core;
  }
  
  public void shutdown()
  {
    taskrunner.shutdownNow();
    // allow a little time for running tasks to stop before
    // returning from this method.
    try { Thread.sleep( 2000 ); } catch (InterruptedException ex) {}
    if ( taskrunner.getActiveCount() != 0 )
      core.logger.error( 
              "Despite 'shutdown now' instruction 2 seconds ago the number of active threads in the task runner is " + 
                      taskrunner.getActiveCount() + 
                      "." );    
  }
  
  
  public void queueTask( BaseTask task ) throws RejectedExecutionException
  {
    try
    { 
      task.setWebAppCore(core);
      ScheduledFuture<?> sf = taskrunner.schedule( task, 0, TimeUnit.SECONDS );
      synchronized ( scheduledtasks )
      {
        scheduledtasks.add( new ScheduledTaskInfo( task, sf ) );
      }
    }
    catch (SecurityException | IllegalArgumentException  ex)
    {
      core.logger.error( "Error attempting to run task.", ex );
    }
  }

  
  public String listTasks()
  {
    StringBuilder sb = new StringBuilder();
    synchronized ( scheduledtasks )
    {
      for ( ScheduledTaskInfo sti: scheduledtasks )
      {
        sb.append( sti.getId() );
        sb.append( "  " );
        sb.append( sti.getTask().getClass().toString() );
        sb.append( "     -     " );
        sb.append( sti.getFuture().isDone()?"DONE":( sti.getFuture().isCancelled()?"CANCELLED":"WAITING/RUNNING" ) );
        sb.append( "\n" );
      }
    }
    return sb.toString();
  }

  
}
