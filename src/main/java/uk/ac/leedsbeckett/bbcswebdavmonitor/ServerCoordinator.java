/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.messagequeue.MessageQueue;
import blackboard.platform.messagequeue.MessageQueueException;
import blackboard.platform.messagequeue.MessageQueueHandler;
import blackboard.platform.messagequeue.MessageQueueMessage;
import blackboard.platform.messagequeue.MessageQueueService;
import blackboard.platform.messagequeue.MessageQueueServiceFactory;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.EntryLockedByCurrentUserException;
import com.xythos.security.api.Context;
import com.xythos.security.api.ContextFactory;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.LockEntry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.log4j.Logger;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.ConfigMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.CoordinationMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.PingMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.PongMessage;
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
  MessageQueueService mqs;
  MessageQueue messagequeue;
  MessageHandler messagehandler=null;
  
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
  final int ping_period = 60*60;
  
  LockEntry[] readyservers = new LockEntry[0];
  HashMap<String,LockEntry> readylocks = new HashMap<>();
  
  ScheduledThreadPoolExecutor housekeeper = new ScheduledThreadPoolExecutor( 1 );
  ScheduledThreadPoolExecutor taskrunner  = new ScheduledThreadPoolExecutor( 1 );
  
          
  public ServerCoordinator( BBMonitor bbmonitor, String pluginname )
  {
    this.bbmonitor = bbmonitor;
    this.pluginname = pluginname;
  }
  
  /**
   * Connect to queues and register handlers.
   * Also starts a new thread that will periodically 'sense' the cluster.
   * 
   * @param bbmonitor The BBMonitor to communicate with.
   * @param queuename The basis for the two queue names.
   * @return 
   */
  public boolean startHouseKeeping()
  {
    try
    {
      mqs = MessageQueueServiceFactory.getInstance();
      messagequeue = mqs.getQueue( pluginname );
      messagequeue.removeMessageHandler();  // just in case
      bbmonitor.coordinationlogger.info( "About to add a handler to the exclusive message queue " + pluginname );
      messagehandler = new MessageHandler( messagequeue, bbmonitor.serverid );
      // NOT exclusive - so messages are broadcast to all listeners
      messagequeue.setMessageHandler( messagehandler, false);
      
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
      
//      this.housekeeper.scheduleAtFixedRate(
//              new Runnable()
//              {
//                    @Override
//                    public void run()
//                    {
//                      if ( otherserverincharge != null )
//                      {
//                        long now = System.currentTimeMillis();
//                        if ( (now - otherserverincharge_timestamp) > (otherserver_timeout*1000L) )
//                        {
//                          bbmonitor.coordinationlogger.warn( "Other server, " + otherserverincharge + " hasn't pinged for a while so probably isn't in charge anymore." );
//                          otherserverincharge = null;
//                          otherserverincharge_timestamp = 0L;
//                        }
//                      }
//                      
//                      if ( iamincharge_lockid == null || stoppending )
//                        return;
//                      bbmonitor.coordinationlogger.debug( "I, " + bbmonitor.serverid + " am in charge, so sending ping to other servers." );
//                      messagehandler.sendMessageToEveryone( new PingMessage() );
//                    }
//              },
//              15L,
//              this.ping_period,
//              TimeUnit.SECONDS );
      
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
  public void stopHouseKeeping()
  {
    stoppending = true;
    
    try { messagequeue.removeMessageHandler(); }
    catch (Exception ex) { bbmonitor.coordinationlogger.error( ex ); }
    
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
    bbmonitor.logger.info( "broadcastConfigChange()" );    
    messagehandler.sendMessageToEveryoneAndSelf( new ConfigMessage() );
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
  
  public void requestTask( String classname, String[] parameters ) throws RejectedExecutionException
  {
    bbmonitor.logger.info( "requestTask" );
    if ( serverincharge == null )
      throw new RejectedExecutionException( "Cannot identify the correct server to run the task on." );
    TaskMessage tm = new TaskMessage( serverincharge, classname, parameters );
    messagehandler.sendMessage( tm, serverincharge );
    bbmonitor.logger.info( "sent task message to " + serverincharge );
  }

  public void queueTask( String classname, String[] parameters ) throws RejectedExecutionException
  {
    bbmonitor.logger.info( "queueTask" );
    try
    { 
      Class c = Class.forName( classname );
      BaseTask task = (BaseTask)c.getConstructor().newInstance();
      task.setBBMonitor( bbmonitor );
      task.setParameters( parameters );
      taskrunner.execute( task );
    }
    catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
    {
      bbmonitor.logger.error( "Error attempting to run task.", ex );
    }
  }
  
  class MessageHandler implements MessageQueueHandler
  {
    MessageQueue messagequeue;
    String serverid;
    
    public MessageHandler( MessageQueue messagequeue, String serverid )
    {
      this.messagequeue = messagequeue;
      this.serverid = serverid;
    }
    
    public void sendMessageToEveryone( CoordinationMessage m )
    {
      sendMessage( m, "everyone", false );
    }
    
    public void sendMessageToEveryoneAndSelf( CoordinationMessage m )
    {
      sendMessage( m, "everyone", true );
    }
    
    public void sendMessage( CoordinationMessage m, String recipient )
    {
      sendMessage( m, recipient, false );
    }
    
    public void sendMessage( CoordinationMessage m, String recipient, boolean andself )
    {
      try    
      {
        m.setSenderId( serverid );
        m.setTransportWire();
        if ( recipient == null )
          m.setRecipientId( "everyone" );
        else
          m.setRecipientId( recipient );
        if ( recipient == null || !serverid.equals( recipient ) )
          messagequeue.sendMessage( m.toMessageQueueMessage() );
        // copy to self over the wire is unreliable so send
        // a copy directly.
        if ( andself || serverid.equals( recipient ) )
        {
          CoordinationMessage dm = m.duplicate();
          dm.setTransportDirect();
          onMessage( dm.toMessageQueueMessage() );
        }
      }
      catch (Exception ex)
      {
        bbmonitor.logger.error( "Unable to send message to peers", ex );
      }
    }
    
    @Override
    public void onMessage(MessageQueueMessage mqm) throws Exception
    {
      try
      {
        handleMessage( mqm );
      }
      catch ( Exception ex )
      {
        bbmonitor.coordinationlogger.debug( "Exception in message handler ", ex );      
        throw ex;
      }
    }
    
    private synchronized void handleMessage(MessageQueueMessage mqm) throws Exception
    {
      //bbmonitor.logger.info( "MessageHandler.onMessage()" );      
      CoordinationMessage m = CoordinationMessage.getMessage( mqm, bbmonitor.logger );
      if ( m == null || m.getRecipientId() == null )
      {
        bbmonitor.coordinationlogger.warn( "Invalid message type." );
        return;
      }

      //bbmonitor.logger.info( "Messsage of type. " + m.getClass() );
      
      // Sending messages to self over the wire seems to only occur
      // if there are no other recipients. So, always ignore such
      // messages and rely on sender to always send messages to self
      // by directly calling the handleMessage method.
      if ( serverid.equals( m.getSenderId() ) && m.isTransportWire() )
        return;

      // bail out if not for everyone and not for me specifically
      if ( !"everyone".equals( m.getRecipientId() ) && !serverid.equals( m.getRecipientId() ) )
        return;
      
      if ( m instanceof ConfigMessage )
      {
        bbmonitor.logger.info( "" + serverid + " received config request from " + m.getSenderId() + "." );
        bbmonitor.reloadSettings();
        return;
      }
      
      if ( m instanceof PingMessage )
      {
        bbmonitor.coordinationlogger.debug( "" + serverid + " received ping from " + m.getSenderId() + ". Sending pong." );
//        if ( m.getSenderId() == null )
//        {
//          bbmonitor.coordinationlogger.warn( "Ping sender is null!!!" );
//          return;
//        }
//        sendMessageToEveryone( new PongMessage() );
//        if ( !m.getSenderId().equals( otherserverincharge ) )
//        {
//          otherserverincharge = m.getSenderId();
//          bbmonitor.coordinationlogger.warn( "New, other server, " + otherserverincharge + " is in charge. (It sent a ping message)" );
//        }
//        otherserverincharge_timestamp = System.currentTimeMillis();
//        return;
      }
      
      if ( m instanceof PongMessage )
      {
        bbmonitor.coordinationlogger.debug( "" + serverid + " received pong from " + m.getSenderId() + "." );
        return;
      }

      if ( m instanceof TaskMessage )
      {
        TaskMessage tm = (TaskMessage)m;
        bbmonitor.coordinationlogger.info( "" + serverid + " received task run request from " + m.getSenderId() + "." );
        if ( iamincharge_lockid != null )
        {
          queueTask( tm.getClassName(), tm.getParameters() );
        }
        return;
      }
    } 
  }  
}

