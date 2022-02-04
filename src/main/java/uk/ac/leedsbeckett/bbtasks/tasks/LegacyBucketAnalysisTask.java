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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author jon
 */
public class LegacyBucketAnalysisTask extends BaseTask
{

  void analyseDirectory(AnalysisBucket bucket, Path dir) throws IOException, InterruptedException
  {
    boolean empty = true;
    ArrayList<Path> filepaths = new ArrayList<>();
    SubBucket subbucket = new SubBucket();
    subbucket.name = dir.toString();
    bucket.dirs.add(subbucket);

    // Look at all regular files under this directory to maximum depth
    try (Stream<Path> stream = Files.find(dir, Integer.MAX_VALUE, (p, atts) -> Files.isRegularFile(p));)
    {
      stream.forEach(new Consumer<Path>()
      {
        public void accept(Path f)
        {
          filepaths.add(f);
        }
      });
    }
    for (Path p : filepaths)
    {
      if ( Thread.interrupted() )
        throw new InterruptedException();
      
      empty = false;
      subbucket.filecount++;
      subbucket.filesize += Files.size(p);
      if (bucket.isContent() && "embedded".equals(p.getParent().getFileName().toString()))
      {
        FileInfo fi = new FileInfo();
        fi.name = p.toString();
        fi.modified = Files.getLastModifiedTime(p).toInstant().atZone(ZoneId.systemDefault());
        fi.size = Files.size(p);
        bucket.fileinfo.add(fi);
      }
    }

    bucket.filecount += subbucket.filecount;
    bucket.filesize += subbucket.filesize;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    BucketMap bucketmap = new BucketMap();
    Path coursebase = webappcore.virtualserverbase.resolve("courses/1/");
    Path logfile = webappcore.logbase.resolve("legacyfilesanalysis-" + webappcore.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");

    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      log.println("Starting to analyse legacy file system. This may take many minutes.");
    } catch (IOException ex)
    {
      debuglogger.error("Error attempting to analyse legacy files.", ex);
      return;
    }

    try
    {
      ArrayList<Path> coursepaths = new ArrayList<>();
      ArrayList<Path> bucketpaths = new ArrayList<>();
      ArrayList<Path> ppgbucketpaths = new ArrayList<>();
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
        if (!Files.isDirectory(f))
        {
          continue;
        }

        if ( i % 100 == 0 || i+1 == coursepaths.size() )
          debuglogger.info( "Checking legacy file system " + i + " of " + coursepaths.size() );

        bucketpaths.clear();
        ppgbucketpaths.clear();
        try (Stream<Path> stream = Files.list(f);)
        {
          stream.forEach(new Consumer<Path>()
          {
            public void accept(Path f)
            {
              if (Files.isDirectory(f) && !f.endsWith("ppg"))
              {
                bucketpaths.add(f);
              }
            }
          });
        }
        Path ppg = f.resolve("ppg");
        if (Files.exists(ppg))
        {
          try (Stream<Path> stream = Files.list(ppg);)
          {
            stream.forEach(new Consumer<Path>()
            {
              public void accept(Path f)
              {
                if (Files.isDirectory(f))
                {
                  ppgbucketpaths.add(f);
                }
              }
            });
          }
        }

        for (Path p : bucketpaths)
        {
          AnalysisBucket b = bucketmap.get(p.getFileName().toString());
          analyseDirectory(b, p);
        }
        for (Path p : ppgbucketpaths)
        {
          AnalysisBucket b = bucketmap.get("ppg/" + p.getFileName().toString());
          analyseDirectory(b, p);
        }
      }
    }
    catch (IOException ex)
    {
      debuglogger.error("Error attempting to analyse legacy files.", ex);
      return;
    }

    debuglogger.info( "Sorting buckets" );
    AnalysisBucket[] a = bucketmap.values().toArray(new AnalysisBucket[bucketmap.size()]);
    Arrays.sort(a, new Comparator<AnalysisBucket>()
    {
      @Override
      public int compare(AnalysisBucket arg0, AnalysisBucket arg1)
      {
        return arg0.name.compareTo(arg1.name);
      }
    });

    debuglogger.info( "Logging buckets to the analysis file" );
    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      log.println(".");
      log.println(".");
      log.println("ANALYSIS BUCKETS ");
      for (AnalysisBucket b : a)
      {
        if ( Thread.interrupted() ) throw new InterruptedException();
        log.println("Bucket " + b.name + " dirs = " + b.dirs.size() + " files = " + b.filecount + "  storage = " + b.filesize);
      }
      log.println(".");
      log.println(".");
      log.println("Detailed report for big buckets.");
      for (AnalysisBucket b : a)
      {
        if ( Thread.interrupted() ) throw new InterruptedException();
        if (b.filesize > 100000000)
        {
          log.println("Big Bucket " + b.name + "   dirs = " + b.dirs.size() + "   files = " + b.filecount + "   storage = " + b.filesize);
          for (SubBucket subbucket : b.dirs)
          {
            if ( Thread.interrupted() ) throw new InterruptedException();
            if (subbucket.filesize > 100000000)
            {
              log.println("   Big Subbucket " + subbucket.name + "   files = " + subbucket.filecount + "   storage = " + subbucket.filesize);
            }
          }
        }
      }
      log.println(".");
      log.println(".");
      log.println("Detailed report for embedded content.");
      AnalysisBucket b = bucketmap.get("content");
      if (b != null)
      {
        for (FileInfo fi : b.fileinfo)
        {
          if ( Thread.interrupted() ) throw new InterruptedException();
          log.print(fi.name.replace(',', ';').replace('/', ','));
          log.print(",");
          log.print(fi.size);
          log.print(",");
          log.print(fi.modified.getYear());
          log.print(",");
          log.print(fi.modified.getMonthValue());
          log.print(",");
          log.println(fi.modified.getDayOfMonth());
        }
      }
      log.println(".");
      log.println(".");
      log.println("End of report.");
      log.println(".");
    }
    catch (IOException ex)
    {
      debuglogger.error( "IO error.", ex );
    }
    debuglogger.info( "Completed task." );

  }

  class SubBucket
  {

    String name;
    int filecount;
    long filesize;
  }

  class FileInfo
  {

    String name;
    ZonedDateTime modified;
    long size;
  }

  class AnalysisBucket
  {

    String name;
    int filecount;
    long filesize;
    ArrayList<SubBucket> dirs = new ArrayList<>();
    ArrayList<FileInfo> fileinfo = new ArrayList<>();

    public boolean isContent()
    {
      return "content".equals(name);
    }
  }

  class BucketMap extends HashMap<String, AnalysisBucket>
  {

    public AnalysisBucket get(Object key)
    {
      AnalysisBucket b = super.get(key);
      if (b == null)
      {
        b = new AnalysisBucket();
        b.name = key.toString();
        put(b.name, b);
      }
      return b;
    }
  }

}
