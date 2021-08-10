/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

/**
 *
 * @author jon
 */
public class StartTimeMessage extends InterserverMessage
{
  public long id;  
  public long starttime;
  public String serverid;
}
