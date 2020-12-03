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
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class uses the MessageQueueService to coordinate this app running on
 * multiple servers in a cluster. Two functions: ensure one server only
 * monitors Xythos and tell all servers to reload configuration if one server
 * saves settings.
 * 
 * @author jon
 */
public class ServerCoordinator extends Thread
{
  BBMonitor bbmonitor;
  boolean active = false;
  boolean stoppending = false;
  String pluginname;
  MessageQueueService mqs;
  MessageQueue messagequeue;
  boolean iamincharge = false;
  boolean waitingforconfirmation = false;
  HashMap<String,String> knownservers = new HashMap<>();
  boolean supress_repeat_error=false;
  long supression_timeout;

          
  public ServerCoordinator(BBMonitor bbmonitor, String pluginname)
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
  public boolean startInterserverMessaging()
  {
    try
    {
      mqs = MessageQueueServiceFactory.getInstance();
      messagequeue = mqs.getQueue( pluginname );
      // exclusive - so only one server instance will actually receive the messages
      bbmonitor.logger.info( "About to add a handler to the exclusive message queue " + pluginname );
      messagequeue.setMessageHandler( new MessageHandler(), false);
      start();
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
  public void stopInterserverMessaging()
  {
    try
    {
      stoppending = true;
      interrupt();
      messagequeue.removeMessageHandler();
    }
    catch (MessageQueueException ex)
    {
      bbmonitor.logger.error( ex );
    }
  }

  /**
   * Tell all servers in the cluster that the configuration has changed and
   * needs to be reloaded. 
   */
  public void broadcastConfigChange()
  {
    try    
    {
      messagequeue.sendMessage( new ConfigMessage( bbmonitor.serverid ) );
    }
    catch (MessageQueueException ex)
    {
      bbmonitor.logger.error( "Unable to send config message to peers" );
      bbmonitor.logger.error( ex );
    }
  }
  
  private String renewLock( String lockid, int lock_lifetime_seconds )
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
        LockEntry lockentry = lockfile.getLock( lockid );
        if ( lockentry != null )
        {
          bbmonitor.logger.debug( "Attempting to renew lock." );
          lockfile.renewLock( lockentry, lock_lifetime_seconds );
          context.commitContext();
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
    return succeeded?lockid:null;
  }
  
  private String createLock( int lock_lifetime_seconds )
  {
    FileSystemEntry lockfile = null;
    Context context = null;
    boolean succeeded = false;
    String lockid=null;
    
    try
    {
      context = ContextFactory.create( bbmonitor.xythosadminuser, new Properties() );
      lockfile = FileSystem.findEntry( bbmonitor.xythosvserver, bbmonitor.lockfilepath, false, context );  
      if ( lockfile != null )
      {
        bbmonitor.logger.debug( "Attempting to lock." );
        LockEntry lockentry = lockfile.lock( LockEntry.EXCLUSIVE_LOCK, LockEntry.ZERO_DEPTH, lock_lifetime_seconds, bbmonitor.serverid );
        context.commitContext();
        lockid = lockentry.getID();
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
    return succeeded?lockid:null;
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
      bbmonitor.logger.error( "Exception while attempting to create a lock." );
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
        bbmonitor.logger.error( "Failed to rollback Xythos context after failed to create lock." );
        bbmonitor.logger.error( ex1 );
      }
    }
  }
  
  
  /**
   * Periodically send heartbeat messages to the exclusive message queue to 
   * find out if this server is the one receiving on that queue.
   * If the message doesn't come back this server is
   * not in charge anymore.
   */
  @Override
  public void run()
  {
    active = true;
    String lockid = null;
    
    int lock_lifetime_seconds = 15;
    
    while ( !stoppending )
    {      
      if ( lockid != null )
        lockid = renewLock( lockid, lock_lifetime_seconds );
      if ( lockid == null )
        lockid = createLock( lock_lifetime_seconds );

      try
      {
        if ( lockid == null )
        {
          bbmonitor.logger.debug( "I don't have the lock." );
          bbmonitor.stopMonitoringXythos();
        }
        else
        {
          bbmonitor.logger.debug( "I have the lock.  " + lockid );
          bbmonitor.startMonitoringXythos();
        }
      }      
      catch ( Exception ex )
      {
        bbmonitor.logger.error( "Exception while updating BBMonitor status." );
        bbmonitor.logger.error( ex );
      }
      
      long timeatloopstart = System.currentTimeMillis();
      long now;
      // pause one second if I don't have the lock
      // if I do have the lock pause for half the life of the lock
      long duration = (lockid==null)?1000L:(lock_lifetime_seconds*1000l/2l);
      do 
      {
        // Short sleep so the thread is responsive to shutting down.
        try { Thread.sleep( 500 ); } catch (InterruptedException ex)
        {
          bbmonitor.logger.debug( "ServerCoordinator woken up early." );
        }
        now = System.currentTimeMillis();
      }
      while ( !stoppending && (now-timeatloopstart) < duration );
    }
    active = false;
    bbmonitor.logger.info( "ServerCoordinator thread ending." );
    
    if ( lockid != null )
      unlock( lockid );
  }
  

  class MessageHandler implements MessageQueueHandler
  {
    @Override
    public void onMessage(MessageQueueMessage mqm) throws Exception
    {
      bbmonitor.logger.debug( "MessageHandler.onMessage()" );
      String messageid = mqm.get( "messageid" ).toString();
      String senderid  = mqm.get( "senderid"  ).toString();
      String type      = mqm.get( "type"      ).toString();
      bbmonitor.logger.debug( "RECEIVED MESSSAGE   id: " + messageid );
      bbmonitor.logger.debug( "                  from: " + senderid );
      bbmonitor.logger.debug( "                  type: " + type     );
      if ( !knownservers.containsKey( senderid ) )
      {
        knownservers.put( senderid, senderid );
        bbmonitor.logger.info( "New message sender: " + senderid );
      }
      
      if ( "config".equals( type ) )
      {
        bbmonitor.reloadSettings();
      }
    } 
  }  
}

/**
 * Tells servers in the cluster to reload configuration.
 * @author jon
 */
class ConfigMessage extends MessageQueueMessage
{
  public ConfigMessage( String senderid )
  {
    Random r = new Random();
    set("messageid", "id_" + Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString(r.nextLong()) );
    set("senderid",  senderid );
    set("type",      "config" );
  }  
}
