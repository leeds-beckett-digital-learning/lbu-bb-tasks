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
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.ConfigMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.CoordinationMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.PingMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.PongMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.TaskMessage;

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
  
  String otherserverincharge=null;
  long otherserverincharge_timestamp;
  long otherserver_timeout = 120;
  
  long lastlocktime = -1L;
  int lock_lifetime_seconds = 15;
  int lock_check_period = 5;
  int ping_period = 30;
  
  ScheduledThreadPoolExecutor housekeeper = new ScheduledThreadPoolExecutor( 1 );
  
          
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
      bbmonitor.logger.info( "About to add a handler to the exclusive message queue " + pluginname );
      messagehandler = new MessageHandler( messagequeue, bbmonitor.serverid, bbmonitor.logger );
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
                      checkCoordinatingLock();
                    }
              },
              0L,
              lock_check_period,
              TimeUnit.SECONDS );
      
      this.housekeeper.scheduleAtFixedRate(
              new Runnable()
              {
                    @Override
                    public void run()
                    {
                      if ( otherserverincharge != null )
                      {
                        long now = System.currentTimeMillis();
                        if ( (now - otherserverincharge_timestamp) > (otherserver_timeout*1000L) )
                        {
                          bbmonitor.logger.warn( "Other server, " + otherserverincharge + " hasn't pinged for a while so probably isn't in charge anymore." );
                          otherserverincharge = null;
                          otherserverincharge_timestamp = 0L;
                        }
                      }
                      
                      if ( iamincharge_lockid == null || stoppending )
                        return;
                      bbmonitor.logger.debug( "I, " + bbmonitor.serverid + " am in charge, so sending ping to other servers." );
                      messagehandler.sendMessageToEveryone( new PingMessage() );
                    }
              },
              15L,
              this.ping_period,
              TimeUnit.SECONDS );
      
    }
    catch (Exception ex)
    {
      bbmonitor.logger.error( "Failed to access message queue." );
      bbmonitor.logger.error( ex );
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
    catch (Exception ex) { bbmonitor.logger.error( ex ); }
    
    try
    {
      housekeeper.shutdownNow();
      bbmonitor.logger.info( "Housekeeper shut down." );
    }
    catch (Exception ex) { bbmonitor.logger.error( ex ); }
    
    if ( iamincharge_lockid != null )
    {
      unlock( iamincharge_lockid );
      iamincharge_lockid = null;
    }
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
  
  private String renewLock( String p_lockid, int lock_lifetime_seconds )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    
    try
    {
      bbmonitor.logger.debug( "Renew lock." );
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry( bbmonitor.xythosvserver, bbmonitor.lockfilepath, false, context );  
      if ( lockfile != null )
      {
        LockEntry lockentry = lockfile.getLock( p_lockid );
        if ( lockentry != null )
        {
          bbmonitor.logger.debug( "Attempting to renew lock." );
          lockfile.renewLock( lockentry, lock_lifetime_seconds );
          context.commitContext();
          lastlocktime = System.currentTimeMillis();
          succeeded = true;
        }
      }
    }
    catch ( Exception ex )
    {
      bbmonitor.logger.error( "Exception while attempting to renew lock." );
      bbmonitor.logger.error( ex );
    }
    
    if ( context != null && !succeeded )
    {
      try
      {
        context.rollbackContext();
      }
      catch (XythosException ex1)
      {
        bbmonitor.logger.error( "Failed to rollback Xythos context after failed to renew lock." );
        bbmonitor.logger.error( ex1 );
      }
    }
    return succeeded?p_lockid:null;
  }
  
  private String createLock( int lock_lifetime_seconds )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    String l_lockid=null;
    
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry( bbmonitor.xythosvserver, bbmonitor.lockfilepath, false, context );  
      if ( lockfile != null )
      {
        bbmonitor.logger.debug( "Attempting to lock." );
        LockEntry lockentry = lockfile.lock( LockEntry.EXCLUSIVE_LOCK, LockEntry.ZERO_DEPTH, lock_lifetime_seconds, bbmonitor.serverid );
        context.commitContext();
        l_lockid = lockentry.getID();
        lastlocktime = System.currentTimeMillis();
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
        bbmonitor.logger.error( "Exception while attempting to create a lock. (This error will be supressed for 1 hour." );
        bbmonitor.logger.error( ex );
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
        bbmonitor.logger.error( "Failed to rollback Xythos context after failed to create lock." );
        bbmonitor.logger.error( ex1 );
      }
    }
    return succeeded?l_lockid:null;
  }
  
  private void unlock( String lockid )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry( bbmonitor.xythosvserver, bbmonitor.lockfilepath, false, context );  
      if ( lockfile != null )
      {
        LockEntry lockentry = lockfile.getLock( lockid );
        if ( lockentry != null )
        {
          bbmonitor.logger.debug( "Attempting to unlock." );
          lockfile.deleteLock( lockentry );
          context.commitContext();
          succeeded = true;
        }
      }
    }
    catch ( Exception ex )
    {
      bbmonitor.logger.error( "Exception while attempting to remove a lock.", ex );
    }
    
    if ( context != null && !succeeded )
    {
      try
      {
        context.rollbackContext();
      }
      catch (XythosException ex1)
      {
        bbmonitor.logger.error( "Failed to rollback Xythos context after failed to create lock." );
        bbmonitor.logger.error( ex1 );
      }
    }
  }
  
  
  public void checkCoordinatingLock()
  {
    long now = System.currentTimeMillis();
    
    if ( iamincharge_lockid != null )
    {
      // If this instance has the lock already don't
      // bother renewing it if it is still quite
      // young.
      long life = (now - lastlocktime)/1000;
      if ( life < (lock_lifetime_seconds/2) )
        return;
      iamincharge_lockid = renewLock(iamincharge_lockid, lock_lifetime_seconds );
    }
    if ( iamincharge_lockid == null )
    {
      iamincharge_lockid = createLock( lock_lifetime_seconds );
      if ( iamincharge_lockid != null )
        bbmonitor.logger.warn("I have gained the lock and am in charge.  " + iamincharge_lockid );
    }

    try
    {
      if ( iamincharge_lockid == null )
      {
        bbmonitor.logger.debug( "I don't have the lock." );
        bbmonitor.stopMonitoringXythos();
      }
      else
      {
        bbmonitor.logger.debug("I have the lock.  " + iamincharge_lockid );
        if ( otherserverincharge != null )
        {
          bbmonitor.logger.warn( "Other server, " + otherserverincharge + " is not in charge any more because I am." );
          otherserverincharge = null;
          otherserverincharge_timestamp = 0L;
        }
        bbmonitor.startMonitoringXythos();
      }
    }      
    catch ( Exception ex )
    {
      bbmonitor.logger.error( "Exception while updating BBMonitor status." );
      bbmonitor.logger.error( ex );
    }
  }
  

  class MessageHandler implements MessageQueueHandler
  {
    MessageQueue messagequeue;
    String serverid;
    Logger logger;
    
    public MessageHandler( MessageQueue messagequeue, String serverid, Logger logger )
    {
      this.messagequeue = messagequeue;
      this.serverid = serverid;
      this.logger = logger;
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
        if ( andself )
        {
          CoordinationMessage dm = m.duplicate();
          dm.setTransportDirect();
          onMessage( dm.toMessageQueueMessage() );
        }
      }
      catch (Exception ex)
      {
        logger.error( "Unable to send config message to peers" );
        logger.error( ex );
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
        logger.debug( "Exception in message handler ", ex );      
        throw ex;
      }
    }
    
    private synchronized void handleMessage(MessageQueueMessage mqm) throws Exception
    {
      //bbmonitor.logger.info( "MessageHandler.onMessage()" );      
      CoordinationMessage m = CoordinationMessage.getMessage( mqm, bbmonitor.logger );
      if ( m == null || m.getRecipientId() == null )
      {
        logger.warn( "Invalid message type." );
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
        logger.info( "" + serverid + " received config request from " + m.getSenderId() + "." );
        bbmonitor.reloadSettings();
        return;
      }
      
      if ( m instanceof PingMessage )
      {
        logger.debug( "" + serverid + " received ping from " + m.getSenderId() + ". Sending pong." );
        if ( m.getSenderId() == null )
        {
          logger.warn( "Ping sender is null!!!" );
          return;
        }
        sendMessageToEveryone( new PongMessage() );
        if ( !m.getSenderId().equals( otherserverincharge ) )
        {
          otherserverincharge = m.getSenderId();
          logger.warn( "New, other server, " + otherserverincharge + " is in charge. (It sent a ping message)" );
        }
        otherserverincharge_timestamp = System.currentTimeMillis();
        return;
      }
      
      if ( m instanceof PongMessage )
      {
        logger.debug( "" + serverid + " received pong from " + m.getSenderId() + "." );
        return;
      }

      if ( m instanceof TaskMessage )
      {
        logger.info( "" + serverid + " received task run request from " + m.getSenderId() + "." );
        return;
      }
    } 
  }  
}

