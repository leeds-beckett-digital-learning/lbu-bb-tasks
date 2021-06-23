/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.util.concurrent.ScheduledFuture;

/**
 *
 * @author jon
 */
public class ScheduledTaskInfo
{
  static long n = 100000;
          
  private final String id;
  private final BaseTask task;
  private final ScheduledFuture<?> future;

  public ScheduledTaskInfo(BaseTask task, ScheduledFuture<?> future)
  {
    id = Long.toString( n++ );
    this.task = task;
    this.future = future;
  }

  public String getId()
  {
    return id;
  }

  public BaseTask getTask()
  {
    return task;
  }

  public ScheduledFuture<?> getFuture()
  {
    return future;
  }
}
