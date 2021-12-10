/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.messaging;

import uk.ac.leedsbeckett.bbb2utils.union.Union;
import uk.ac.leedsbeckett.bbb2utils.union.UnionMember;
import uk.ac.leedsbeckett.bbtasks.tasks.*;

/**
 *
 * @author jon
 */
public class TaskUnion extends Union<BaseTask>
{
  @UnionMember public AnalyseVideoTask                     u1;
  @UnionMember public DemoTask                             u2;
  @UnionMember public FfmpegDemoTask                       u3;
  @UnionMember public LegacyBucketAnalysisTask             u4;
  @UnionMember public LegacySearchTask                     u5;
  @UnionMember public LegacyTurnitinAnalysisTask           u6;
  @UnionMember public LegacyTurnitinPruneTask              u7;
  @UnionMember public XythosAnalyseAutoArchiveTask         u8;
  @UnionMember public XythosAnalyseDeletedAutoArchiveTask  u9;
  @UnionMember public XythosListDeletedFilesTask           u10;
  @UnionMember public PeerServiceTask                      u11;
  @UnionMember public XythosMoveHugeCourseFilesTask        u12;
}
