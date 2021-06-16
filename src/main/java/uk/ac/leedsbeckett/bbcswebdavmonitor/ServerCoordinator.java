/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import com.xythos.common.api.XythosException;
import com.xythos.security.api.Context;
import com.xythos.security.api.ContextFactory;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.LockEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.ConfigMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.InterserverMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.MessageDispatcher;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.TaskMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.BaseTask;

/**
 * This class uses the MessageQueueService to coordinate this app running on
 * multiple servers in a cluster. Two functions: ensure one server only
 * monitors Xythos and tell all servers to reload configuration if one server
 * saves settings.
 * 
 * @author jon
 */
public class ServerCoordinator
{
  BBMonitor bbmonitor;
  boolean active = false;
  boolean stoppending = false;
  String pluginname;
  String coordinationservleturl;
  
  boolean waitingforconfirmation = false;
  boolean supress_repeat_error=false;
  long supression_timeout;

  String iamincharge_lockid=null;
  String iamready_lockid=null;
  
  String serverincharge = null;
  long otherserver_timeout = 120;
  
  long iamincharge_lastlocktime = -1L;
  long iamready_lastlocktime = -1L;
  final int lock_lifetime_seconds = 30;
  final int lock_check_period = 15;
  
  LockEntry[] readyservers = new LockEntry[0];
  HashMap<String,LockEntry> readylocks = new HashMap<>();
  
  ScheduledThreadPoolExecutor housekeeper = new ScheduledThreadPoolExecutor( 1 );
  ScheduledThreadPoolExecutor taskrunner  = new ScheduledThreadPoolExecutor( 1 );
  
  MessageDispatcher messagedispatcher;
          
  public ServerCoordinator( BBMonitor bbmonitor, String pluginname, String coordinationservleturl )
  {
    this.bbmonitor = bbmonitor;
    this.pluginname = pluginname;
    this.coordinationservleturl = coordinationservleturl;
    bbmonitor.logger.info( "ServerCoordinator " + coordinationservleturl );
    messagedispatcher = new MessageDispatcher( coordinationservleturl, bbmonitor.logger );
  }
  
  /**
   * Connect to queues and register handlers.
   * Also starts a new thread that will periodically 'sense' the cluster.
   * 
   * @param bbmonitor The BBMonitor to communicate with.
   * @param queuename The basis for the two queue names.
   * @return 
   */
  public boolean startPollingLocks()
  {
    try
    {      
      this.housekeeper.scheduleAtFixedRate(
              new Runnable()
              {
                    @Override
                    public void run()
                    {
                      if ( stoppending )
                        return;
                      checkCoordinatingLocks();
                    }
              },
              0L,
              lock_check_period,
              TimeUnit.SECONDS );      
    }
    catch (Exception ex)
    {
      bbmonitor.coordinationlogger.error( "Failed to access message queue.", ex );
      return false;
    }
    
    return true;
  }

  /**
   * Disconnect from queues.
   */
  public void stopCoordinating()
  {
    stoppending = true;
    
    try
    {
      housekeeper.shutdownNow();
      bbmonitor.coordinationlogger.info( "Housekeeper shut down." );
    }
    catch (Exception ex) { bbmonitor.coordinationlogger.error( "Error shutting down", ex ); }
    
    if ( iamincharge_lockid != null )
    {
      unlock( bbmonitor.primarylockfilepath, iamincharge_lockid );
      iamincharge_lockid = null;
    }
    
    if ( iamready_lockid != null )
    {
      unlock( bbmonitor.secondarylockfilepath, iamready_lockid );
      iamready_lockid = null;
    }
    
    taskrunner.shutdownNow();
    // allow a little time for running tasks to stop before
    // returning from this method.
    try { Thread.sleep( 2000 ); } catch (InterruptedException ex) {}
    if ( taskrunner.getActiveCount() != 0 )
      bbmonitor.coordinationlogger.error( 
              "Despite 'shutdown now' instruction 2 seconds ago the number of active threads in the task runner is " + 
                      taskrunner.getActiveCount() + 
                      "." );
  }

  /**
   * Tell all servers in the cluster that the configuration has changed and
   * needs to be reloaded. 
   */
  public void broadcastConfigChange()
  {
    sendMessageToEveryone( new ConfigMessage() );
  }
  
  private String renewLock( String path, String p_lockid, int lock_lifetime_seconds )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    
    try
    {
      bbmonitor.coordinationlogger.debug( "Renew lock on " + path );
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry(bbmonitor.xythosvserver, path, false, context );  
      if ( lockfile != null )
      {
        LockEntry lockentry = lockfile.getLock( p_lockid );
        if ( lockentry != null )
        {
          bbmonitor.coordinationlogger.debug( "Attempting to renew lock. "  + path );
          lockfile.renewLock( lockentry, lock_lifetime_seconds );
          context.commitContext();
          iamincharge_lastlocktime = System.currentTimeMillis();
          succeeded = true;
        }
      }
    }
    catch ( Exception ex )
    {
      bbmonitor.coordinationlogger.error( "Exception while attempting to renew lock." + path, ex );
    }
    
    if ( context != null && !succeeded )
    {
      try
      {
        context.rollbackContext();
      }
      catch (XythosException ex1)
      {
        bbmonitor.coordinationlogger.error( "Failed to rollback Xythos context after failed to renew lock.", ex1 );
      }
    }
    return succeeded?p_lockid:null;
  }
  
  private LockEntry[] getLockers( String path )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    LockEntry[] locks = new LockEntry[0];
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry(bbmonitor.xythosvserver, path, false, context );  
      if ( lockfile != null )
        locks = lockfile.getLocks();
    }    
    catch ( Exception ex )
    {
      bbmonitor.coordinationlogger.error( "Finding locks on " + path + " failed.", ex );
    }
    
    bbmonitor.coordinationlogger.debug( "    Locks on " + path + " :" );
    for ( LockEntry l : locks )
      bbmonitor.coordinationlogger.debug( "        " + l.getID() + " " + l.getWebdavLockOwner() );
    bbmonitor.coordinationlogger.debug( "    End of list." );

    return locks;
  }
  
  private String createLock( String path, int lock_lifetime_seconds, int lock_type )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    String l_lockid=null;
    
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry(bbmonitor.xythosvserver, path, false, context );  
      if ( lockfile != null )
      {
        bbmonitor.coordinationlogger.debug( "Attempting to lock." );
        LockEntry lockentry = lockfile.lock( lock_type, LockEntry.ZERO_DEPTH, lock_lifetime_seconds, bbmonitor.serverid );
        context.commitContext();
        l_lockid = lockentry.getID();
        iamincharge_lastlocktime = System.currentTimeMillis();
        succeeded = true;
        supress_repeat_error = false;
      }
    }
    catch ( Exception ex )
    {
      if ( supress_repeat_error )
      {
        long now = System.currentTimeMillis();
        if ( now >= supression_timeout )
          supress_repeat_error = false;
      }
      
      if ( !supress_repeat_error )
      {
        bbmonitor.coordinationlogger.error( "Exception while attempting to create a lock. (This error will be supressed for 1 hour." );
        bbmonitor.coordinationlogger.error( ex );
        supress_repeat_error = true;
        supression_timeout = System.currentTimeMillis() + (1000L * 60L * 60L);
      }
    }
    
    if ( context != null && !succeeded )
    {
      try
      {
        context.rollbackContext();
      }
      catch (XythosException ex1)
      {
        bbmonitor.coordinationlogger.error( "Failed to rollback Xythos context after failed to create lock.", ex1 );
      }
    }
    return succeeded?l_lockid:null;
  }
  
  private void unlock( String path, String lockid )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry(bbmonitor.xythosvserver, bbmonitor.primarylockfilepath, false, context );  
      if ( lockfile != null )
      {
        LockEntry lockentry = lockfile.getLock( lockid );
        if ( lockentry != null )
        {
          bbmonitor.coordinationlogger.debug( "Attempting to unlock." );
          lockfile.deleteLock( lockentry );
          context.commitContext();
          succeeded = true;
        }
      }
    }
    catch ( Exception ex )
    {
      bbmonitor.coordinationlogger.error( "Exception while attempting to remove a lock.", ex );
    }
    
    if ( context != null && !succeeded )
    {
      try
      {
        context.rollbackContext();
      }
      catch (XythosException ex1)
      {
        bbmonitor.logger.error( "Failed to rollback Xythos context after failed to unlock.", ex1 );
      }
    }
  }
  
  
  public void checkCoordinatingLocks()
  {
    long now = System.currentTimeMillis();
    
    bbmonitor.coordinationlogger.debug( 
            "checkCoordinatingLock() secondary: " + iamready_lockid + 
            " primary: " + iamincharge_lockid );
    
    if ( iamready_lockid != null )
      iamready_lockid = renewLock( bbmonitor.secondarylockfilepath, iamready_lockid, lock_lifetime_seconds );
    // If I didn't have the lock or the renewal failed:
    if ( iamready_lockid == null )
    {
      // This is a shared lock - because any server can be ready.
      iamready_lockid = createLock( bbmonitor.secondarylockfilepath, lock_lifetime_seconds, LockEntry.SHARED_LOCK );
      if ( iamready_lockid != null )
        bbmonitor.coordinationlogger.warn("I have created a 'ready' lock so others know I'm ready.  " + iamready_lockid );
    }

    if ( iamincharge_lockid != null )
      iamincharge_lockid = renewLock( bbmonitor.primarylockfilepath, iamincharge_lockid, lock_lifetime_seconds );
    if ( iamincharge_lockid == null )
    {
      iamincharge_lockid = createLock( bbmonitor.primarylockfilepath, lock_lifetime_seconds, LockEntry.EXCLUSIVE_LOCK );
      if ( iamincharge_lockid != null )
        bbmonitor.coordinationlogger.warn("I have created a primary lock so others know I am in charge.  " + iamincharge_lockid );
    }
    
    // Discover the in charge server...
    //if ( iamincharge_lockid == null )
    String newserverincharge = null;
    LockEntry[] inchargeservers = getLockers( bbmonitor.primarylockfilepath );
    if ( inchargeservers.length > 0 )
      newserverincharge = inchargeservers[0].getWebdavLockOwner();
    if ( serverincharge == null )
    {
      if ( newserverincharge != null )
        bbmonitor.coordinationlogger.warn("There is now a server in charge.  " + newserverincharge );
    }
    else
    {
      if ( newserverincharge == null )
        bbmonitor.coordinationlogger.warn("No server is in charge now." );
      else if ( !serverincharge.equals( newserverincharge ) )
        bbmonitor.coordinationlogger.warn("Server in charge has changed from " + serverincharge + " to " + newserverincharge );
    }
    serverincharge = newserverincharge;
    
    // Discover the other ready servers...
    synchronized( readylocks )
    {
      LockEntry[] newreadyservers = getLockers( bbmonitor.secondarylockfilepath );
      boolean changed=false;
      if ( readyservers.length != newreadyservers.length )
        changed = true;
      for ( int i=0; i<readyservers.length; i++ )
        if ( !readyservers[i].getID().equals( newreadyservers[i].getID() ) )
        {
          changed = true;
          break;
        }      
      if ( changed )
      {
        readyservers = newreadyservers;
        bbmonitor.coordinationlogger.warn( "Ready server list changed." );
        readylocks.clear();
        for ( LockEntry l : readyservers )
        {
          readylocks.put( l.getWebdavLockOwner(), l );
          bbmonitor.coordinationlogger.warn( "   Server: " + l.getWebdavLockOwner() );
        }
      }
    }

    
    try
    {
      if ( iamincharge_lockid == null )
      {
        bbmonitor.coordinationlogger.debug( "I don't have the primary lock." );
        bbmonitor.stopMonitoringXythos();
      }
      else
      {
        bbmonitor.coordinationlogger.debug("I have the lock.  " + iamincharge_lockid );
        bbmonitor.startMonitoringXythos();
      }
    }      
    catch ( Exception ex )
    {
      bbmonitor.coordinationlogger.error( "Exception while updating BBMonitor status.", ex );
    }
  }
  
  
  public void requestTask( BaseTask task ) throws RejectedExecutionException
  {
    if ( serverincharge == null )
    {
      bbmonitor.logger.error( "Unable to request task because I don't know which server is in charge." );
      return;
    }
    TaskMessage message = new TaskMessage( task );
    sendMessage( message, serverincharge );
  }
  

  public void queueTask( BaseTask task ) throws RejectedExecutionException
  {
    try
    { 
      task.setBBMonitor( bbmonitor );
      taskrunner.execute( task );
    }
    catch (SecurityException | IllegalArgumentException  ex)
    {
      bbmonitor.logger.error( "Error attempting to run task.", ex );
    }
  }
  
  public void sendMessageToEveryone( InterserverMessage m )
  {
    sendMessageToEveryone( m, false );
  }

  public void sendMessageToEveryoneElse( InterserverMessage m )
  {
    sendMessageToEveryone( m, true );
  }

  public void sendMessageToEveryone( InterserverMessage m, boolean excludeself )
  {
    ArrayList<LockEntry> locks = new ArrayList<LockEntry>();
    synchronized ( this.readylocks )
    {
      locks.addAll( readylocks.values() );
    }
    
    m.setSenderId( bbmonitor.serverid );
    for ( LockEntry l : locks )
    {
      if ( excludeself && bbmonitor.serverid.equals( l.getWebdavLockOwner() ) )
        continue;
      sendMessage( m, l.getWebdavLockOwner() );
    }
  }  
  
  private void sendMessage( InterserverMessage m, String recipient )
  {
    m.setSenderId( bbmonitor.serverid );
    m.setRecipientId( recipient );
    messagedispatcher.dispatch( m );
  }

  public void handleMessage( InterserverMessage message )
  {
    bbmonitor.logger.debug( "ServerCoordinator.handleMessage()" );      
    if ( message == null || message.getRecipientId() == null )
    {
      bbmonitor.logger.error( "Invalid message type." );
      return;
    }

    // bail out if not for me
    if ( !bbmonitor.serverid.equals( message.getRecipientId() ) )
      return;

    if ( message instanceof ConfigMessage )
    {
      bbmonitor.logger.info( "" + bbmonitor.serverid + " received config request from " + message.getSenderId() + "." );
      bbmonitor.reloadSettings();
      return;
    }

    if ( message instanceof TaskMessage )
    {
      TaskMessage tm = (TaskMessage)message;
      bbmonitor.logger.info( "" + bbmonitor.serverid + " received task run request from " + message.getSenderId() + "." );
      if ( iamincharge_lockid != null )
        queueTask( tm.getTask() );
      return;
    }
  }
  
  
}

