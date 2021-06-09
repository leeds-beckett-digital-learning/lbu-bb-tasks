/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

import blackboard.platform.messagequeue.MessageQueueMessage;

/**
 *
 * @author jon
 */
public class PongMessage extends CoordinationMessage
{
  public PongMessage( MessageQueueMessage mqm )
  {
    super( mqm );
  }
  
  public PongMessage()
  {
    super();
  }    
}
