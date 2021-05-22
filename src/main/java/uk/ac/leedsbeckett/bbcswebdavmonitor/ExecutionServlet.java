/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.utils.IOUtils;

/**
 *
 * @author jon
 */
@WebServlet("/execute/*")
public class ExecutionServlet extends AbstractServlet
{
  ExecutionThread t;
  
  @Override
  public void init() throws ServletException
  {
    super.init();
    installResources();
  }
  
  
  public void installResources()
  {
    File base = new File( bbmonitor.pluginbase.toFile(), "ffmpegbin" );
    if ( !base.isDirectory() )
      base.mkdir();
    
    try ( BufferedReader reader = new BufferedReader( new InputStreamReader( getClass().getClassLoader().getResourceAsStream("/uk/ac/leedsbeckett/bbcswebdavmonitor/resources/ffmpegbin/filelist.txt") ) ); )
    {
      Stream<String> slines = reader.lines();
      Object[] olines = slines.toArray();
      for ( Object oline : olines )
      {
        String filename = oline.toString().trim();
        if ( filename.length() == 0 || filename.endsWith( "/filelist.txt" ) )
          continue;
        String resourcename = "/uk/ac/leedsbeckett/bbcswebdavmonitor/resources/ffmpegbin/" + filename;
        File file = new File( bbmonitor.pluginbase.toFile(), "ffmpegbin/" + filename );
        bbmonitor.logger.info( "Checking " + file );
        if ( !file.isFile() )
        {
          try ( InputStream   in = getClass().getClassLoader().getResourceAsStream( resourcename );
                OutputStream out = new FileOutputStream( file ) )
          {
            IOUtils.copy(in, out);
          }
        }        
      }

      File ffmpegfile = new File( bbmonitor.pluginbase.toFile(), "ffmpegbin/ffmpeg" );
      if ( ffmpegfile.exists() && ffmpegfile.isFile() )
        ffmpegfile.setExecutable(true);
    }
    catch (IOException ex)
    {
      Logger.getLogger(ExecutionServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
  
  
  /**
   * Works out which page of information to present and calls the appropriate
   * method.
   * 
   * @param req The request data.
   * @param resp The response data
   * @throws ServletException
   * @throws IOException 
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {  
    // Make sure that the user is authenticated and is a system admin.
    // Bail out if not.
    try
    {
      if ( !PlugInUtil.authorizeForSystemAdmin(req, resp) )
        return;
      if ( t != null )
      {
        sendError( req, resp, "Process already running." );
        return;
      }
      t = new ExecutionThread();
      t.start();
      sendError( req, resp, "Process started. See log file for results." );
    }
    catch ( Exception e )
    {
      bbmonitor.logger.error( "Error ", e );
      sendError( req, resp, "Error " + e.toString() );
    }
  }
  
  
  protected class ExecutionThread extends Thread
  {
    public void run()
    {
      try
      {
        File logfile = new File( bbmonitor.pluginbase.toFile(), "ffmpeg_log.txt" );
        if ( logfile.exists() )
          logfile.delete();
        File executablefile = new File( bbmonitor.pluginbase.toFile(), "ffmpegbin/ffmpeg" );
        File infile         = new File( bbmonitor.pluginbase.toFile(), "in.mp4"           );
        File outfile        = new File( bbmonitor.pluginbase.toFile(), "out.mp4"          );
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
        pb.redirectOutput( Redirect.appendTo( logfile ) );
        Process p = pb.start();
        while ( p.isAlive() )
        {
          try {p.waitFor( 2, TimeUnit.SECONDS );} catch (InterruptedException ex){}
        }
      }
      catch ( Exception ex )
      {
        bbmonitor.logger.error( "Exception ", ex );
      }
      finally
      {
        t=null;
      }
    }
  }
}
