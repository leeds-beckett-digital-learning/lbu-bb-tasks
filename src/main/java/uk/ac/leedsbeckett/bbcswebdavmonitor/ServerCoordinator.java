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
import java.util.HashMap;
import java.util.Random;

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
  String exclusivequeuename;
  String peerqueuename;
  MessageQueueService mqs;
  MessageQueue exclusivemessagequeue;
  MessageQueue peermessagequeue;

  boolean iamincharge = false;
  boolean waitingforconfirmation = false;
  HeartbeatMessage currentheartbeat = null;
  
  HashMap<String,String> knownservers = new HashMap<>();
  
  
  /**
   * Connect to queues and register handlers.
   * Also starts a new thread that will periodically 'sense' the cluster.
   * 
   * @param bbmonitor The BBMonitor to communicate with.
   * @param queuename The basis for the two queue names.
   * @return 
   */
  public boolean startInterserverMessaging( BBMonitor bbmonitor, String queuename )
  {
    this.bbmonitor = bbmonitor;
    exclusivequeuename = "x_" + queuename;
    peerqueuename = "p_" + queuename;
    try
    {
      mqs = MessageQueueServiceFactory.getInstance();
      exclusivemessagequeue = mqs.getQueue( exclusivequeuename );
      // exclusive - so only one server instance will actually receive the messages
      bbmonitor.logger.info( "About to add a handler to the exclusive message queue " + exclusivequeuename );
      exclusivemessagequeue.setMessageHandler( new ExclusiveHandler(), true);
      exclusivemessagequeue.sendMessage( new HeartbeatMessage( bbmonitor.serverid ) );
      
      mqs = MessageQueueServiceFactory.getInstance();
      peermessagequeue = mqs.getQueue( peerqueuename );
      if ( peermessagequeue.getHasMessageHandler() )
      {
        bbmonitor.logger.error( "Peer message queue already has a handler." );
        peermessagequeue.removeMessageHandler();
      }
      bbmonitor.logger.info( "About to add a handler to the peer message queue " + peerqueuename );
      // not exclusive - so all instances get a copy of each message
      peermessagequeue.setMessageHandler( new PeerHandler(), false );
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
      exclusivemessagequeue.removeMessageHandler();
      // can we send a message after removing the handler?
      // If so, this allows the baton to be passed on quickly.
      exclusivemessagequeue.sendMessage( new HeartbeatMessage( bbmonitor.serverid ) );
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
      peermessagequeue.sendMessage( new ConfigMessage( bbmonitor.serverid ) );
    }
    catch (MessageQueueException ex)
    {
      bbmonitor.logger.error( "Unable to send config message to peers" );
      bbmonitor.logger.error( ex );
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
    while ( !stoppending )
    {      
      try      
      {
        currentheartbeat = new HeartbeatMessage( bbmonitor.serverid );
        if ( iamincharge ) 
          waitingforconfirmation =true;
        if ( waitingforconfirmation )
          bbmonitor.logger.debug( "Am I still in charge?" );
        else
          bbmonitor.logger.debug( "Am I in charge now?" );
        exclusivemessagequeue.sendMessage( currentheartbeat );
      }
      catch (MessageQueueException ex)
      {
        bbmonitor.logger.error( ex );
      }

      try { Thread.sleep( 10000 ); } catch (InterruptedException ex) {}
      if ( waitingforconfirmation )
      {
        // timed out
        bbmonitor.logger.info( "This instance not in charge of monitoring anymore because no hearbeat came in ten seconds." );
        bbmonitor.stopMonitoringXythos();
        iamincharge = false;
        waitingforconfirmation = false;
      }
    }
    active = false;
  }
  

  class ExclusiveHandler implements MessageQueueHandler
  {
    @Override
    public void onMessage(MessageQueueMessage mqm) throws Exception
    {
      bbmonitor.logger.debug( "ExclusiveHandler.onMessage()" );
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
      if ( "heartbeat".equals( type ) )
      {
        boolean gainingcharge=false;
        if ( iamincharge )
          bbmonitor.logger.debug( "I am still in charge." );
        else
        {
          gainingcharge=true;
          bbmonitor.logger.info( "This instance is now in control of monitor actions." );
          bbmonitor.startMonitoringXythos();
        }
        iamincharge = true;
        waitingforconfirmation = false;
        if ( gainingcharge )
          interrupt();
      }
    } 
  }
  

  class PeerHandler implements MessageQueueHandler
  {
    @Override
    public void onMessage(MessageQueueMessage mqm) throws Exception
    {
      bbmonitor.logger.debug( "PeerHandler.onMessage()" );
      String messageid = mqm.get( "messageid" ).toString();
      String senderid  = mqm.get( "senderid"  ).toString();
      String type      = mqm.get( "type"      ).toString();
      bbmonitor.logger.debug( "RECEIVED MESSSAGE   id: " + messageid );
      bbmonitor.logger.debug( "                  from: " + senderid );
      bbmonitor.logger.debug( "                  type: " + type     ); 
      if ( "config".equals( type ) )
      {
        bbmonitor.reloadSettings();
      }
    } 
  }  
}



/**
 * Heartbeat message used to determine which server in a cluster is in 
 * charge. (We allow the message queue manager to detect when servers go
 * up and down and select which one will receive heartbeats.)
 * 
 * @author jon
 */
class HeartbeatMessage extends MessageQueueMessage
{
  public HeartbeatMessage( String senderid )
  {
    Random r = new Random();
    set("messageid", "id_" + Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString(r.nextLong()) );
    set("senderid",  senderid );
    set("type",      "heartbeat" );
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
