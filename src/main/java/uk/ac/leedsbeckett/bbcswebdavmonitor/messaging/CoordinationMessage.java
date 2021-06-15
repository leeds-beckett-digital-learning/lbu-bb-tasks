/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

import blackboard.platform.messagequeue.MessageQueueMessage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author jon
 */
public abstract class CoordinationMessage
{
  MessageQueueMessage mqm;
  boolean incoming = false;
  
  static Random r = new Random();
  
  public static CoordinationMessage getMessage( MessageQueueMessage mqm, Logger logger )
  {
    //logger.info( "CoordinationMessage.getMessage()" );
    CoordinationMessage m = null;
    try
    {
      String strc = mqm.get("messageclass");
      //logger.info( "have messageclass = [" + strc + "]" );
      if ( strc == null ) return null;
      Class c = Class.forName( strc );
      //logger.info( "have class" );
      c = c.asSubclass( CoordinationMessage.class );
      //logger.info( "have subclass" );
      Constructor cd = c.getConstructor( MessageQueueMessage.class );
      //logger.info( "have constructor" );
      m = (CoordinationMessage)cd.newInstance( mqm );
      //logger.info( "have instance" );
    }
    catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
    {
      logger.error( "Exception in CoordinationMessage.getMessage()", ex );
      return null;
    }
    return m;
  }
  
  public CoordinationMessage( MessageQueueMessage mqm )
  {
    incoming = true;    
    this.mqm = mqm;
  }

  public CoordinationMessage()
  {
    incoming = false;
    mqm = new MessageQueueMessage();
    mqm.set("messageid", "id_" + Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString(r.nextLong()) );
    mqm.set("messageclass", this.getClass().getName() );
    mqm.set("messagesenderid",  "unknown" );
    mqm.set("messagetransport",  "unknown" );
    mqm.set("messagerecipientid",  "everyone" );
  }

  public CoordinationMessage duplicate() throws Exception
  {
    Class c = getClass();
    Constructor cd = c.getConstructor();
    CoordinationMessage m = (CoordinationMessage)cd.newInstance();
    m.incoming = incoming;
    m.mqm = new MessageQueueMessage();
    mqm.getEntrySet().forEach( entry -> { m.mqm.set( entry.getKey(), entry.getValue() ); } );
    return m;
  }

  
  
  public MessageQueueMessage toMessageQueueMessage()
  {
    return mqm;
  }

  public boolean isIncoming()
  {
    return incoming;
  }

  public void setSenderId( String id )
  {
    mqm.set( "messagesenderid", id );
  }
  
  public String getSenderId()
  {
    return mqm.get( "messagesenderid" );
  }  
  
  public void setRecipientId( String id )
  {
    mqm.set( "messagerecipientid", id );
  }
  
  public String getRecipientId()
  {
    return mqm.get( "messagerecipientid" );
  }  
  
  public void setTransportWire()
  {
    mqm.set( "messagetransport", "wire" );
  }
  
  public void setTransportDirect()
  {
    mqm.set( "messagetransport", "direct" );
  }
  
  public boolean isTransportWire()
  {
    return "wire".equals( mqm.get( "messagetransport" ) );
  }  
  
  public boolean isTransportDirect()
  {
    return "direct".equals( mqm.get( "messagetransport" ) );
  }  
  
}
