/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.ac.leedsbeckett.bbtasks.tasks.BaseTask;

/**
 *
 * @author jon
 */
public class TaskMessage extends InterserverMessage
{
  public TaskUnion taskunion = new TaskUnion();
  
  public TaskMessage()
  {
    super();
  }
  
  @JsonIgnore
  public void setTask( BaseTask task )
  {
    taskunion.set( task );
  }
  
  @JsonIgnore
  public BaseTask getTask()
  {
    return taskunion.get();
  }
}
