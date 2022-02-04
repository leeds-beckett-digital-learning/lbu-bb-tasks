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

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author jon
 */
public class FfmpegDemoTask extends BaseTask
{
  public void doTask() throws InterruptedException
  {
    Process p = null;
    try
    {
      File base = webappcore.pluginbase.toFile();
      File logfile = new File( base, "ffmpeg_log.txt" );
      if ( logfile.exists() )
        logfile.delete();
      File executablefile = new File( base, "ffmpegbin/ffmpeg" );
      File infile         = new File( base, "in.mp4"           );
      File outfile        = new File( base, "out.mp4"          );
      if ( outfile.exists() )
        outfile.delete();
      ProcessBuilder pb = new ProcessBuilder( 
              executablefile.getAbsolutePath(), 
              "-nostdin", 
              //"-nostats",
              "-i", infile.getAbsolutePath(),
              "-c:v", "libx264", 
              "-crf", "40",
              outfile.getAbsolutePath()
      );
      pb.directory( executablefile.getParentFile() );
      pb.environment().put( "LD_LIBRARY_PATH", "." );
      pb.redirectErrorStream( true );
      pb.redirectOutput( ProcessBuilder.Redirect.appendTo( logfile ) );
      p = pb.start();
      while ( p.isAlive() )
      {
        p.waitFor( 2, TimeUnit.SECONDS );
      }
    }
    catch ( InterruptedException ie )
    {
      if ( p != null )
        p.destroy();
      throw ie;
    }
    catch ( Exception ex )
    {
      webappcore.logger.error( "Exception ", ex );
    }
  }  
}
