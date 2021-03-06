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
import java.math.BigInteger;
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
public class LegacyTurnitinPruneTask extends BaseTask
{
  @Override
  public void doTask() throws InterruptedException
  {
    Path logfile = webappcore.logbase.resolve("turnitinpruning-" + webappcore.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");
    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      log.println("Starting to prune turnitin files in legacy file system. This may take many minutes.");
    } catch (IOException ex)
    {
      webappcore.logger.error("Error attempting to prune turnitin files.", ex);
      return;
    }

    long start = System.currentTimeMillis();
    webappcore.logger.info("Turn It In pruning process started.");
    int filesdeleted = 0;

    Path coursebase = webappcore.virtualserverbase.resolve("courses/1");
    BigInteger totalgood = BigInteger.ZERO;
    BigInteger totalbad = BigInteger.ZERO;

    try
    {
      ArrayList<Path> coursepaths = new ArrayList<>();
      ArrayList<Path> filepaths = new ArrayList<>();
      // get a list in a way that autocloses the directory
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
        filepaths.clear();
        String courseid = f.getFileName().toString();
        Path uploadssource = f.resolve("ppg").resolve("BB_Direct").resolve("Uploads");
        webappcore.logger.info("Working on: " + i + " of " + coursepaths.size() + " = " + uploadssource.toString());
        if (Files.exists(uploadssource) && Files.isDirectory(uploadssource))
        {
          // get a list in a way that autocloses the directory
          try (Stream<Path> stream = Files.list(uploadssource);)
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
              // changing criteria for deletion - file was created more than 1 hour ago
              Instant onehourago = Instant.now().minus(1, ChronoUnit.HOURS);
              Instant mod = Files.getLastModifiedTime(f).toInstant();
              webappcore.logger.debug("Processing : " + uploadedfile.toString() + "   " + mod);
              if (mod.isBefore(onehourago))
              {
                String name = uploadedfile.getFileName().toString();
                webappcore.logger.debug("Deleting " + name);
                Files.delete( uploadedfile );
                filesdeleted++;
              }
            }
          }
        }
      }
    }
    catch ( IOException ex )
    {
      webappcore.logger.error("Error attempting to prune turnitin files.");
      webappcore.logger.error(ex);
    }

    long end = System.currentTimeMillis();
    float elapsed = 0.001f * (float) (end - start);
    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      log.println("Turn It In pruning process ended after " + elapsed + " seconds. " + filesdeleted + " files deleted.");
    }
    catch ( IOException ex )
    {
      webappcore.logger.error("Error attempting to prune turnitin files.", ex);
    }
  }
}
