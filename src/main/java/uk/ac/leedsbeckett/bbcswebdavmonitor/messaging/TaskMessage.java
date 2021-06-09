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
public class TaskMessage extends CoordinationMessage
{
  public TaskMessage( MessageQueueMessage mqm )
  {
    super( mqm );
  }
 
  public TaskMessage( String taskserverid, String taskclassname, String[] parameters )
  {
    super();
    mqm.set( "taskserverid",  taskserverid  );
    mqm.set( "taskclassname", taskclassname );
    StringBuilder builder = new StringBuilder();
    if ( parameters == null )
      mqm.set( "parameter_count", "0" );
    else
    {
      mqm.set( "parameter_count", Integer.toString( parameters.length ) );
      for ( int i=0; i<parameters.length; i++ )
        if ( parameters[i] != null )
          mqm.set( "parameter_" + i, parameters[i] );
    }
  }
  
  public String getServerId()
  {
    return mqm.get( "taskserverid" );
  }

  public String getClassName()
  {
    return mqm.get( "taskclassname" );
  } 
}
