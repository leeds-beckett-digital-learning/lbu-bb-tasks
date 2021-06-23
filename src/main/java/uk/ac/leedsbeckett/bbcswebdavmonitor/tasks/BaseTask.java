/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.io.Serializable;
import uk.ac.leedsbeckett.bbcswebdavmonitor.BBMonitor;
import uk.ac.leedsbeckett.bbcswebdavmonitor.ServerCoordinator;

/**
 *
 * @author jon
 */
public abstract class BaseTask implements Runnable, Serializable
{
  transient BBMonitor bbmonitor = null;
  transient ServerCoordinator servercoordinator = null;
  
  public BBMonitor getBBMonitor()
  {
    return bbmonitor;
  }

  public void setBBMonitor( BBMonitor bbmonitor )
  {
    this.bbmonitor = bbmonitor;
  }

  public ServerCoordinator getServerCoordinator()
  {
    return servercoordinator;
  }

  public void setServerCoordinator( ServerCoordinator servercoordinator )
  {
    this.servercoordinator = servercoordinator;
  }
  
  public final void run()
  {
    try
    {
      bbmonitor.logger.debug( "Starting task - " + getClass() );
      doTask();
      bbmonitor.logger.debug( "Completed task normally - " + getClass() );
    }
    catch ( InterruptedException ie )
    {
      bbmonitor.logger.debug( "Task was interrupted - " + getClass() );      
    }
    catch ( Throwable th )
    {
      bbmonitor.logger.error( "Task stopped abnormally " + getClass(), th );
    }
  }
  
  public abstract void doTask() throws InterruptedException;
}
