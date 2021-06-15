/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import uk.ac.leedsbeckett.bbcswebdavmonitor.BBMonitor;

/**
 *
 * @author jon
 */
public abstract class BaseTask implements Runnable
{
  BBMonitor bbmonitor = null;
  String[] parameters = new String[0];
  boolean validParameters=true;

  public BBMonitor getBBMonitor()
  {
    return bbmonitor;
  }

  public void setBBMonitor( BBMonitor bbmonitor )
  {
    this.bbmonitor = bbmonitor;
  }
  
  public String[] getParameters()
  {
    return parameters;
  }

  public void setParameters(String[] parameters)
  {
    this.parameters = parameters;
  }

  public boolean isValidParameters()
  {
    return validParameters;
  }

  void setValidParameters( boolean validParameters )
  {
    this.validParameters = validParameters;
  }
  
  
}
