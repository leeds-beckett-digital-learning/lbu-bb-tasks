/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

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
    Path logfile = bbmonitor.logbase.resolve("turnitinfilesanalysis-" + bbmonitor.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");
    Path coursebase = bbmonitor.virtualserverbase.resolve("courses/1/");
    long totalgood = 0L;
    long totalbad = 0L;

    try
    {
      try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
      {
        log.println("Starting to analyse turnitin files in legacy file system. This may take many minutes.");
      } catch (IOException ex)
      {
        bbmonitor.logger.error("Error attempting to analyse turnitin files.", ex);
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
            bbmonitor.logger.info("Checking redundant tii files in " + i + " of " + coursepaths.size() + " " + uploads.toString());
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
        bbmonitor.logger.error("Error attempting to analyse turnitin files.", ex);
      }
    }
    catch ( IOException ex )
    {
      bbmonitor.logger.error("Error attempting to analyse turnitin files.");
      bbmonitor.logger.error(ex);
    }
  }
}
