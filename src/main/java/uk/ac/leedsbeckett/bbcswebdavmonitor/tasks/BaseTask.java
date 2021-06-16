/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.io.Serializable;
import uk.ac.leedsbeckett.bbcswebdavmonitor.BBMonitor;

/**
 *
 * @author jon
 */
public abstract class BaseTask implements Runnable, Serializable
{
  transient BBMonitor bbmonitor = null;
  
  public BBMonitor getBBMonitor()
  {
    return bbmonitor;
  }

  public void setBBMonitor( BBMonitor bbmonitor )
  {
    this.bbmonitor = bbmonitor;
  }
    
}
