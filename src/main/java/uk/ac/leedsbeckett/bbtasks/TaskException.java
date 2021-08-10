/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

/**
 *
 * @author jon
 */
public class TaskException extends Exception
{

  public TaskException(String message)
  {
    super(message);
  }

  public TaskException(String message, Throwable cause)
  {
    super(message, cause);
  }
  
}
