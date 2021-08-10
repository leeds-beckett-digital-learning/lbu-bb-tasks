/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

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
public abstract class InterserverMessage
{  
  public InterserverMessage()
  {
  }
}
