/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

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
      File base = bbmonitor.pluginbase.toFile();
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
      bbmonitor.logger.error( "Exception ", ex );
    }
  }  
}
