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

import java.util.concurrent.ScheduledFuture;

/**
 *
 * @author jon
 */
public class ScheduledTaskInfo
{
  static long n = 100000;
          
  private final String id;
  private final BaseTask task;
  private final ScheduledFuture<?> future;

  public ScheduledTaskInfo(BaseTask task, ScheduledFuture<?> future)
  {
    id = Long.toString( n++ );
    this.task = task;
    this.future = future;
  }

  public String getId()
  {
    return id;
  }

  public BaseTask getTask()
  {
    return task;
  }

  public ScheduledFuture<?> getFuture()
  {
    return future;
  }
}
