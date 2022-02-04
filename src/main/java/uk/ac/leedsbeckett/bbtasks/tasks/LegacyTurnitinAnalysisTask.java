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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author jon
 */
public class LegacyTurnitinAnalysisTask extends BaseTask
{

  @Override
  public void doTask() throws InterruptedException
  {
    Path logfile = webappcore.logbase.resolve("turnitinfilesanalysis-" + webappcore.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");
    Path coursebase = webappcore.virtualserverbase.resolve("courses/1/");
    long totalgood = 0L;
    long totalbad = 0L;

    try
    {
      try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
      {
        log.println("Starting to analyse turnitin files in legacy file system. This may take many minutes.");
      } catch (IOException ex)
      {
        webappcore.logger.error("Error attempting to analyse turnitin files.", ex);
        return;
      }

      ArrayList<Path> coursepaths = new ArrayList<>();
      ArrayList<Path> filepaths = new ArrayList<>();
      try (Stream<Path> stream = Files.list(coursebase);)
      {
        stream.forEach(new Consumer<Path>()
        {
          public void accept(Path f)
          {
            coursepaths.add(f);
          }
        });
      }
      for (int i = 0; i < coursepaths.size(); i++)
      {
        if ( Thread.interrupted() ) throw new InterruptedException();
        Path f = coursepaths.get(i);
        if (Files.isDirectory(f))
        {
          String courseid = f.getFileName().toString();
          Path uploads = f.resolve("ppg/BB_Direct/Uploads/");
          if (Files.exists(uploads) && Files.isDirectory(uploads))
          {
            webappcore.logger.info("Checking redundant tii files in " + i + " of " + coursepaths.size() + " " + uploads.toString());
            filepaths.clear();
            try (Stream<Path> stream = Files.list(uploads);)
            {
              stream.forEach(new Consumer<Path>()
              {
                public void accept(Path f)
                {
                  filepaths.add(f);
                }
              });
            }

            for (Path uploadedfile : filepaths)
            {
              if ( Thread.interrupted() ) throw new InterruptedException();
              if (Files.isRegularFile(uploadedfile))
              {
                Instant onehourago = Instant.now().minus( 10, ChronoUnit.MINUTES );
                Instant mod = Files.getLastModifiedTime(f).toInstant();
                if (!mod.isBefore(onehourago))
                {
                  totalgood += Files.size(uploadedfile);
                } else
                {
                  totalbad += Files.size(uploadedfile);
                }
              }
            }
          }
        }
      }

      try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
      {
        log.println("Analysis of turnitin submissions in legacy file system.");
        log.println("Bytes of data that won't be pruned = " + totalgood);
        log.println("Bytes of data that will be pruned = " + totalbad);
        log.println("End of report.");
      } catch (Exception ex)
      {
        webappcore.logger.error("Error attempting to analyse turnitin files.", ex);
      }
    }
    catch ( IOException ex )
    {
      webappcore.logger.error("Error attempting to analyse turnitin files.");
      webappcore.logger.error(ex);
    }
  }
}
