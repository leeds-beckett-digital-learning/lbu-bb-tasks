/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

import uk.ac.leedsbeckett.bbb2utils.messaging.MessageHeader;

/**
 *
 * @author jon
 */
public interface CoordinatorClientMessageListener
{
  public void receiveMessage( MessageHeader header, InterserverMessage message );
}
