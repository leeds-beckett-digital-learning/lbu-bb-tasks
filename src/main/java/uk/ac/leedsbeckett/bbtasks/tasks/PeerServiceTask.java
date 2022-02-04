/*
 * Copyright 2022 Leeds Beckett University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.leedsbeckett.bbtasks.tasks;

import blackboard.platform.discovery.PeerService;
import blackboard.platform.discovery.impl.PeerServiceDAO;

/**
 *
 * @author jon
 */
public class PeerServiceTask extends BaseTask
{
  @Override
  public void doTask() throws InterruptedException
  {
    debuglogger.info( "Peer service task started." );
    long started = System.currentTimeMillis();
    
    PeerServiceDAO dao = PeerServiceDAO.get();
    StringBuilder sb = new StringBuilder();
    sb.append( "Peer service table contents:\n" );
    sb.append( "id\tnodeid\tserviceid\tpayload\tlastseen\tmandel\n" );
    for ( PeerService s : dao.loadAll() )
    {
      sb.append( s.getId().toExternalString() );
      sb.append( "\t" );
      sb.append( s.getNodeId() );
      sb.append( "\t" );
      sb.append( s.getServiceId() );
      sb.append( "\t" );
      sb.append( s.getPayload() );
      sb.append( "\t" );
      sb.append( s.getLastSeen().toString() );
      sb.append( "\t" );
      sb.append( s.isManualDeleteInd()?'Y':'N' );
      sb.append( "\t" );
      sb.append( s.isPendingRestartInd()?'Y':'N' );
      sb.append( "\n" );
    }
    debuglogger.info( sb.toString() );
    
    long now = System.currentTimeMillis();
    debuglogger.info( "Demo task completed in " + (now-started) + " milliseconds." );
  }
}
