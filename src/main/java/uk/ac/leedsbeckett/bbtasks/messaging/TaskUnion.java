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
  @UnionMember public XythosArchiveHugeCourseFilesArchiveBlobsTask    u13;
  @UnionMember public XythosArchiveHugeCourseFilesStageTwoTask    u14;
  @UnionMember public XythosArchiveHugeCourseFilesAnalysis        u15;
  @UnionMember public SendCustomEmailTask                         u16;
}
