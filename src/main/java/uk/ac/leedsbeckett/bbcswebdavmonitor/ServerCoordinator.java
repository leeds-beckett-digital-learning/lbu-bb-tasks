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
import org.apache.log4j.LogManager;

/**
 *
 * @author jon
 */
public class ServerCoordinator extends Thread implements MessageQueueHandler
{
  ContextListener cl;
  boolean active = false;
  boolean stoppending = false;
  MessageQueueService mqs;
  MessageQueue messagequeue;

  boolean iamincharge = false;
  boolean waitingforconfirmation = false;
  HeartbeatMessage currentheartbeat = null;
  
  HashMap<String,String> knownservers = new HashMap<>();
  
  public boolean startInterserverMessaging( ContextListener cl, String queuename )
  {
    this.cl = cl;
    mqs = MessageQueueServiceFactory.getInstance();
    try
    {
      messagequeue = mqs.getQueue( queuename );
      // exclusive - so only one instance will actually receive the messages
      messagequeue.setMessageHandler( this, true);
      messagequeue.sendMessage( new HeartbeatMessage( cl.instanceid ) );
      start();
    }
    catch (Exception ex)
    {
      ContextListener.logToBuffer( "Failed to access message queue." );
      ContextListener.logToBuffer( ex );
      return false;
    }
    
    return true;
  }

  public void stopInterserverMessaging()
  {
    try
    {
      stoppending = true;
      interrupt();
      messagequeue.removeMessageHandler();
      // can we send a message after removing the handler?
      // If so, this allows the baton to be passed on quickly.
      messagequeue.sendMessage( new HeartbeatMessage( cl.instanceid ) );
    }
    catch (MessageQueueException ex)
    {
      ContextListener.logger.error( ex );
    }
  }

  
  @Override
  public void run()
  {
    active = true;
    while ( !stoppending )
    {      
      try      
      {
        currentheartbeat = new HeartbeatMessage( cl.instanceid );
        if ( iamincharge ) 
          waitingforconfirmation =true;
        if ( waitingforconfirmation )
          ContextListener.logger.debug("Am I still in charge?" );
        else
          ContextListener.logger.debug( "Am I in charge now?" );
        messagequeue.sendMessage( currentheartbeat );
      }
      catch (MessageQueueException ex)
      {
        ContextListener.logger.error( ex );
      }

      try { Thread.sleep( 10000 ); } catch (InterruptedException ex) {}
      if ( waitingforconfirmation )
      {
        // timed out
        ContextListener.logger.info( "This instance not in charge of monitoring anymore because no hearbeat came in ten seconds." );
        cl.stopMonitoringXythos();
        iamincharge = false;
        waitingforconfirmation = false;
      }
    }
    active = false;
  }

  @Override
  public void onMessage(MessageQueueMessage mqm) throws Exception
  {
    String messageid = mqm.get( "messageid" ).toString();
    String senderid  = mqm.get( "senderid"  ).toString();
    String type      = mqm.get( "type"      ).toString();
    
    ContextListener.logger.debug( "RECEIVED MESSSAGE   id: " + messageid );
    ContextListener.logger.debug( "                  from: " + senderid );
    ContextListener.logger.debug( "                  type: " + type     );

    if ( !knownservers.containsKey( senderid ) )
    {
      knownservers.put( senderid, senderid );
      ContextListener.logger.info( "New message sender: " + senderid );
    }
    
    boolean gainingcharge=false;
    if ( iamincharge )
      ContextListener.logger.debug( "I am still in charge." );
    else
    {
      gainingcharge=true;
      ContextListener.logger.info( "This instance is now in control of monitor actions." );
      cl.startMonitoringXythos();
    }
    iamincharge = true;
    waitingforconfirmation = false;
    if ( gainingcharge )
      interrupt();
  }
  
}


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
