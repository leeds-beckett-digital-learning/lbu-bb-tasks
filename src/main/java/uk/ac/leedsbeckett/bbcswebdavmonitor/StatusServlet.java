/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 *
 * @author jon
 */
@WebServlet("/status")
public class StatusServlet extends HttpServlet
{  
  DateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss z" );
          
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    try
    {
      if ( !PlugInUtil.authorizeForSystemAdmin(req, resp) )
        return;
    }
    catch ( Exception e )
    {
      throw new ServletException( e );
    }
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      String delete = req.getParameter("delete");
      String log = req.getParameter("log");
      if ( log != null && log.length() > 0 )
        sendLog( out, log );
      else if ( delete != null && delete.length() > 0 )
      {
        ArrayList<String> files = new ArrayList<>();
        for ( String key : req.getParameterMap().keySet() )
        {
          ContextListener.logger.info( key );
          if ( key.startsWith( "delete_" ) )
              files.add( key.substring( 7 ) );
        }
        sendDelete( out, files );
      }
      else
        sendHome( out );
    }
  }
  
  void sendHome( ServletOutputStream out ) throws IOException
  {
    
    out.println( "<!DOCTYPE html>\n<html><body><h1>DAV Monitor Status</h1><h2>Bootstrap Log</h2>" );
    out.println( "<p>This bootstrap log comes from whichever server instance " +
                 "you are connected to and contains logging before the log file " +
                 "was initiated.</p>" );
    
    out.println( "<pre>" );
    out.println( ContextListener.getLog() );
    out.println( "</pre>" );

    out.println( "<h2>Logs on file</h2>" );
    out.println( "<p>These logs are from all server instances.</p>" );
    String folder = ContextListener.getLogFolder();
    if ( folder != null )
    {
      File f = new File( folder );
      if ( f.exists() )
      {
        File[] logfiles = f.listFiles( new FilenameFilter(){
          @Override
          public boolean accept(File dir, String name)
          {
            return name.endsWith( ".log" );
          }
        });
        Arrays.sort(logfiles, new Comparator<File>(){
          @Override
          public int compare(File o1, File o2)
          {
            return Long.compare( o1.lastModified(), o2.lastModified() );
          }
        });
        out.println( "<form action=\"status\" method=\"GET\">" );
        out.println( "<input type=\"hidden\" name=\"delete\" value=\"delete\"/>" );
        out.println( "<table border=\"0\">");
        for ( File lf : logfiles )
        {
          if ( lf.isFile() )
          {
            out.print( "<tr><td><input type=\"checkbox\" name=\"delete_" );
            out.print( lf.getName() );
            out.println( "\"/></td>" );
            out.print( "<td><a href=\"status?log=" );
            out.print( lf.getName() );
            out.print( "\">" );
            out.print( lf.getName() );
            out.println( "</a></td>" );
            out.print( "<td>" );
            out.print( df.format( lf.lastModified() ) );
            out.println( "</td></tr>" );
          }
        }
        out.println( "<p><input type=\"Submit\" value=\"Delete Selected\"/></p>" );
        out.println( "</table></form>");
      }
    }
    out.println( "</pre></body></html>" );
  }
  
  void sendLog( ServletOutputStream out, String logname ) throws IOException
  {
    out.print( "<!DOCTYPE html>\n<html><body>" );
    out.print( "<p><a href=\"status\">Back</a></p>" );
    out.print( "<h1>Log - " );
    out.print( logname );
    out.print( "</h1><pre>" );
    
    File logfile = new File( ContextListener.getLogFolder() + '/' + logname );

    try ( FileReader reader = new FileReader( logfile ); 
            Writer w = new OutputStreamWriter( out ) )
    {
      reader.transferTo( w );
    }
    catch ( IOException ioex )
    {
      out.println( "Exception reading log file.\n" );
    }
    
    out.println( "</pre></body></html>" );
  }


  void sendDelete( ServletOutputStream out, List<String> files ) throws IOException
  {
    out.print( "<!DOCTYPE html>\n<html><body>" );
    out.print( "<p><a href=\"status\">Back</a></p>" );
    out.println( "<h1>Deleting log files</h1>" );
    
    File d = new File( ContextListener.getLogFolder() );

    for ( String fn : files )
    {
      if ( fn.contains( "/" ) || fn.contains( "/" ) )
      {
        out.println( "<p>Can't delete " + fn + " because it has slashes in the name.</p>" );
        continue;
      }
      
      try
      {
        File f = new File( d, fn );
        f.delete();
        out.println( "<p>Deleted " + fn + "</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Exception trying to delete " + fn + "</p>" );
        ContextListener.logger.error( "Unable to delete log file." );
        ContextListener.logger.error( e );
      }
    }
  }
}
