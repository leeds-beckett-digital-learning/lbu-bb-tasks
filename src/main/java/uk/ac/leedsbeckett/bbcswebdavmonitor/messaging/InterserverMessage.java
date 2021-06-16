/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

import blackboard.platform.messagequeue.MessageQueueMessage;
import java.io.Serializable;
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
public abstract class InterserverMessage implements Serializable
{
  static Random r = new Random();
  
  
  transient boolean incoming = false;
  
  String messageid;
  String messagesenderid    = "unknown";
  String messagetransport   = "unknown";
  String messagerecipientid = "everyone";
  
  public InterserverMessage()
  {
    messageid = "id_" + Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString(r.nextLong());
  }

  public void setIncoming( boolean incoming )
  {
    this.incoming = incoming;
  }
  
  public boolean isIncoming()
  {
    return incoming;
  }

  public void setSenderId( String id )
  {
    messagesenderid = id;
  }
  
  public String getSenderId()
  {
    return messagesenderid;
  }  
  
  public void setRecipientId( String id )
  {
    messagerecipientid = id;
  }
  
  public String getRecipientId()
  {
    return messagerecipientid;
  }  
  
  public void setTransportWire()
  {
    messagetransport = "wire";
  }
  
  public void setTransportDirect()
  {
    messagetransport = "direct";
  }
  
  public boolean isTransportWire()
  {
    return "wire".equals( messagetransport );
  }  
  
  public boolean isTransportDirect()
  {
    return "direct".equals( messagetransport );
  }  
  
}
