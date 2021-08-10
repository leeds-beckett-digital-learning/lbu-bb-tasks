/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

import uk.ac.leedsbeckett.bbb2utils.union.Union;
import uk.ac.leedsbeckett.bbb2utils.union.UnionMember;

/**
 *
 * @author jon
 */
public class MessageUnion extends Union<InterserverMessage>
{
  @UnionMember public ConfigMessage           config;
  @UnionMember public RequestTaskListMessage  rqlist;
  @UnionMember public TaskListMessage         list;
  @UnionMember public TaskMessage             task;
  @UnionMember public RequestStartTimeMessage rqstart;
  @UnionMember public StartTimeMessage        start;
}
