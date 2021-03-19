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
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * This servlet provides the user interface to BB system administrators.
 * Gives access to logs and ability to reconfigure.
 * 
 * @author jon
 */
@WebServlet("/status")
public class StatusServlet extends HttpServlet
{  
  DateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss z" );
  BBMonitor bbmonitor;

  /**
   * Get a reference to the right instance of BBMonitor from an attribute which
   * that instance put in the servlet context.
  */
  @Override
  public void init() throws ServletException
  {
    super.init();
    bbmonitor = (BBMonitor)getServletContext().getAttribute( BBMonitor.ATTRIBUTE_CONTEXTBBMONITOR );
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
    }
    catch ( Exception e )
    {
      throw new ServletException( e );
    }

    // Which page is wanted?
    String delete = req.getParameter("delete");
    String log = req.getParameter("log");
    String setup = req.getParameter("setup");
    String setupsave = req.getParameter("setupsave");
    BuildingBlockProperties props = bbmonitor.getProperties();
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"index.html\">Home</a></p>" );      
      out.println( "<h1>DAV Monitor Status</h1>" );
      
      if ( log != null && log.length() > 0 )
        sendLog( out, log );
      else if ( delete != null && delete.length() > 0 )
      {
        ArrayList<String> files = new ArrayList<>();
        for ( String key : req.getParameterMap().keySet() )
        {
          bbmonitor.logger.info( key );
          if ( key.startsWith( "delete_" ) )
              files.add( key.substring( 7 ) );
        }
        sendDelete( out, files );
      }
      else if ( setup != null && setup.length() > 0)
        sendSetup( out, props );
      else if ( setupsave != null && setupsave.length() > 0)
        sendSetupSave( req, out, props );
      else
        sendBootstrap( out );
        //sendHome( out );
      
      out.println( "</body></html>" );
    }
  }
  
  
  /**
   * Output a list of log files that can be viewed or deleted.
   * @param out
   * @throws IOException 
   */
  void sendHome( ServletOutputStream out ) throws IOException
  {
    out.println( "<h2>Logs on file</h2>" );
    out.println( "<p>These logs are from all server instances.</p>" );
    String folder = bbmonitor.getLogFolder();
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
        out.println( "</table>");
        out.println( "<p><input type=\"Submit\" value=\"Delete Selected\"/></p>" );
        out.println( "</form>");
      }
    }
  }
  
  /**
   * Output a list of log files that can be viewed or deleted.
   * @param out
   * @throws IOException 
   */
  void sendBootstrap( ServletOutputStream out ) throws IOException
  {
    out.println( "<h2>Bootstrap Log</h2>" );
    out.println( "<p>This bootstrap log comes from whichever server instance " +
                 "you are connected to and contains logging before the log file " +
                 "was initiated.</p>" );    
    out.println( "<pre>" );
    out.println( BBMonitor.getBootstrapLog() );
    out.println( "</pre>" );
  }
  
  
  /**
   * Output a selected log file.
   * @param out
   * @param logname
   * @throws IOException 
   */
  void sendLog( ServletOutputStream out, String logname ) throws IOException
  {
    out.print( "<h2>Log - " );
    out.print( logname );
    out.print( "</h2><pre>" );
    
    File logfile = new File( bbmonitor.getLogFolder() + '/' + logname );

    try ( FileReader reader = new FileReader( logfile ); 
            Writer w = new OutputStreamWriter( out ) )
    {
      reader.transferTo( w );
    }
    catch ( IOException ioex )
    {
      out.println( "Exception reading log file.\n" );
    }
    
    out.println( "</pre>" );
  }


  /**
   * Delete selected log files and send a confirmation page.
   * @param out
   * @param files
   * @throws IOException 
   */
  void sendDelete( ServletOutputStream out, List<String> files ) throws IOException
  {
    out.println( "<h2>Deleting log files</h2>" );
    
    File d = new File( bbmonitor.getLogFolder() );

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
        bbmonitor.logger.error( "Unable to delete log file." );
        bbmonitor.logger.error( e );
      }
    }
    out.println( "</pre>" );
  }

  /**
   * Send a form for settings.
   * 
   * @param out
   * @throws IOException 
   */
  void sendSetup( ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String s = Integer.toString( props.getFileSize() );
    String[][] sizes = 
    {
      {  "100",  "100MB" },
      {  "250",  "250MB" },
      {  "500",  "500MB" },
      { "1024", "1024MB" },
      { "2048", "2048MB" },
      { "5120", "5120MB" }
    };
    String sa = props.getAction();
    String[][] actions = 
    {
      { "none", "None" },
      { "mode1", "Mode 1 Email Notification" },
      { "mode1a", "Mode 1 Email Notification (*admin users only)" }
    };
    Level[] levellist = { Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG };
    Level currentlevel = props.getLogLevel();
    
    out.println( "<h2>Configure Settings</h2>" );
    out.println( "<p>Note: if you want to stop this building block plugin ");
    out.println( "running you should use the Building Blocks link in the ");
    out.println( "Integrations panel of the System Administration page.</p>" );
    
    out.println( "<form name=\"config\" action=\"status\" method=\"GET\">" );
    out.println( "<input type=\"hidden\" name=\"setupsave\" value=\"true\"/>" );
    out.println( "<h3>Big File Log</h3>" );
    out.println( "<p>How big does a file have to be to record it in the log when it is created?</p>" );
    out.println( "<select name=\"filesize\" size=\"6\">" );
    for ( String[] pair : sizes )
      out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(s)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
    out.println( "</select>" );
    out.println( "<h3>Technical Log</h3>" );
    out.println( "<p>How much detail do you want in the technical logs?</p>" );
    out.println( "<select name=\"loglevel\" size=\"4\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (currentlevel.equals(level)?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );
    out.println( "<h3>Action</h3>" );
    out.println( "<p>What action should be taken?</p>" );
    out.println( "<select name=\"action\" size=\"4\">" );
    for ( String[] pair : actions )
      out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(sa)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
    out.println( "</select>" );
    
    out.println( "<h3>EMail</h3>" );
    out.println( "<p>What email address should notifications be sent from?</p>" );
    out.println( "<input name=\"emailfrom\" value=\"" + props.getEMailFrom() + "\"/>" );
    out.println( "<p>What human readble name goes with that address?</p>" );
    out.println( "<input name=\"emailfromname\" value=\"" + props.getEMailFromName() + "\"/>" );
    out.println( "<p>What subject line should the email have?</p>" );
    out.println( "<input name=\"emailsubject\" value=\"" + props.getEMailSubject() + "\"/>" );
    out.println( "<p>What message should be sent to users when they upload a huge video file?</p>" );
    out.println( "<textarea name=\"emailbody\" cols=\"40\" rows=\"10\">" + props.getEMailBody() + "</textarea>" );
    
    out.println( "<h3>Submit</h3>" );
    out.println( "<p><input type=\"submit\" value=\"Save\"/></p>" );
    out.println( "</form>" );
  }

  void sendSetupSave( HttpServletRequest req, ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String filesize      = req.getParameter( "filesize"      );
    String loglevel      = req.getParameter( "loglevel"      );
    String action        = req.getParameter( "action"        );
    String emailsubject  = req.getParameter( "emailsubject"  );
    String emailbody     = req.getParameter( "emailbody"     );
    String emailfrom     = req.getParameter( "emailfrom"     );
    String emailfromname = req.getParameter( "emailfromname" );
    
    out.println( "<h2>Saving Configuration Settings</h2>" );

    try
    {
      props.setLogLevel(   Level.toLevel(  loglevel ) );
      props.setFileSize( Integer.parseInt( filesize ) );
      props.setAction( action );
      props.setEMailSubject( emailsubject );
      props.setEMailBody( emailbody );
      props.setEMailFrom( emailfrom );
      props.setEMailFromName( emailfromname );
      bbmonitor.saveProperties();
    }
    catch ( Throwable th )
    {
      bbmonitor.logger.error( "Unable to save properties.", th );
      out.println( "<p>Technical problem trying to save settings</p>" );
      return;
    }
    out.println( "<p>Saved settings</p>" );
  }
}
