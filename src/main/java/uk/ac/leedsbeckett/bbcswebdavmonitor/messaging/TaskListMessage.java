/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

/**
 *
 * @author jon
 */
public class TaskListMessage extends InterserverMessage
{
  final String list;

  public TaskListMessage(String list)
  {
    this.list = list;
  }

  public String getList()
  {
    return list;
  }
}
