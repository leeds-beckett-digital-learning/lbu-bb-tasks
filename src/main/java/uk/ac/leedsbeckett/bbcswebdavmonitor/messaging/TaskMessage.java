/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

import blackboard.platform.messagequeue.MessageQueueMessage;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.BaseTask;

/**
 *
 * @author jon
 */
public class TaskMessage extends InterserverMessage
{
  BaseTask task;
  
  public TaskMessage( BaseTask task )
  {
    super();
    this.task = task;
  }
  
  public BaseTask getTask()
  {
    return task;
  }
}
