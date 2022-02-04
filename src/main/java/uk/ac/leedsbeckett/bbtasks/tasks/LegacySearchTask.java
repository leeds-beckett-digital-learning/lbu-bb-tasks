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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jon
 */
public class LegacySearchTask extends BaseTask
{

  transient String base = "/usr/local";
  public String search;

  @JsonCreator
  public LegacySearchTask( @JsonProperty("search") String search )
  {
    this.search = search;
  }

  private void listDirectoryMatch(File dir) throws InterruptedException
  {
    webappcore.logger.info("Searching in " + new File(base).getAbsolutePath());
    for (File f : dir.listFiles())
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      if (f.getName().equals(search))
      {
        webappcore.logger.info(f.getAbsolutePath());
      }
      if (f.isDirectory())
      {
        listDirectoryMatch(f);
      }
    }
  }

  @Override
  public void doTask() throws InterruptedException
  {
    listDirectoryMatch( new File(base) );
    webappcore.logger.info("End of legacy file system search.");
  }
}
