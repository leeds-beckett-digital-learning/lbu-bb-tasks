/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Level;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.RequestTaskListMessage;

/**
 * This servlet provides the user interface to BB system administrators.
 * Gives access to logs and ability to reconfigure.
 * 
 * @author jon
 */
@WebServlet("/status/*")
public class StatusServlet extends AbstractServlet
{  
  DateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss z" );

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
    String setup = req.getParameter("setup");
    String tasklist = req.getParameter("tasklist");
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
      
      if ( tasklist != null && tasklist.length() > 0)
        sendTaskList( out );
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
  void sendTaskList( ServletOutputStream out ) throws IOException
  {
    out.println( "<h2>Task List</h2>" );
    out.println( "<h3>Running</h3>" );
    
    try
    {
      servercoordinator.clearTaskList();
      servercoordinator.sendMessage( new RequestTaskListMessage(), servercoordinator.serverincharge );
      out.println( "<p>Successfully requested task.</p>" );
    }
    catch ( Exception e )
    {
      out.println( "<p>Error attempting to request the task.</p></body></html>" );        
      bbmonitor.logger.error( "Error attempting to request the task.", e );
      return;
    }    

    String list = servercoordinator.getTaskList();
    if ( list == null )
    {
      out.println( "<p>Couldn't obtain the task list within time limit.</p></body></html>" );        
      bbmonitor.logger.error( "Timed out waiting for task list." );
      return;
    }    
    
    out.println( "<p><pre>" );
    out.println( list );
    out.println( "</pre></p></body></html>" );
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
    Level currentlevelcoord = props.getLogLevelForCoordination();
    
    out.println( "<h2>Configure Settings</h2>" );
    out.println( "<p>Note: if you want to stop this building block plugin ");
    out.println( "running you should use the Building Blocks link in the ");
    out.println( "Integrations panel of the System Administration page.</p>" );
    
    out.println( "<form name=\"config\" action=\"status\" method=\"GET\">" );
    out.println( "<input type=\"hidden\" name=\"setupsave\" value=\"true\"/>" );

    out.println( "<h3>Technical Log</h3>" );
    out.println( "<p>How much detail do you want in the technical logs?</p>" );
    out.println( "<table><tr><td>Main Log<br/>" );
    out.println( "<select name=\"loglevel\" size=\"4\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (currentlevel.equals(level)?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );
    out.println( "</td><td>Coordination Log<br/>" );
    out.println( "<select name=\"loglevelcoord\" size=\"4\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (currentlevelcoord.equals(level)?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );
    out.println( "</td></tr></table>" );

    out.println( "<h3>Big File Log</h3>" );
    out.println( "<p>How big does a file have to be to record it in the log when it is created?</p>" );
    out.println( "<select name=\"filesize\" size=\"6\">" );
    for ( String[] pair : sizes )
      out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(s)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
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
    
    out.println( "<p>File name matching regular expression?</p>" );
    out.println( "<input name=\"regex\" value=\"" + props.getFileMatchingExpression() + "\"/>" );
    out.println( "<p>What message should be sent to users when they upload a huge video file?</p>" );
    out.println( "<textarea name=\"specialemailbody\" cols=\"40\" rows=\"10\">" + props.getSpecialEMailBody() + "</textarea>" );
    
    out.println( "<h3>Submit</h3>" );
    out.println( "<p><input type=\"submit\" value=\"Save\"/></p>" );
    out.println( "</form>" );
  }

  void sendSetupSave( HttpServletRequest req, ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String filesize         = req.getParameter( "filesize"         );
    String loglevel         = req.getParameter( "loglevel"         );
    String loglevelcoord    = req.getParameter( "loglevelcoord"    );
    String action           = req.getParameter( "action"           );
    String emailsubject     = req.getParameter( "emailsubject"     );
    String emailbody        = req.getParameter( "emailbody"        );
    String regex            = req.getParameter( "regex"            );
    String specialemailbody = req.getParameter( "specialemailbody" );
    String emailfrom        = req.getParameter( "emailfrom"        );
    String emailfromname    = req.getParameter( "emailfromname"    );
    
    out.println( "<h2>Saving Configuration Settings</h2>" );

    try
    {
      props.setLogLevel(   Level.toLevel(  loglevel ) );
      props.setLogLevelForCoordination( Level.toLevel(  loglevelcoord ) );
      props.setFileSize( Integer.parseInt( filesize ) );
      props.setAction( action );
      props.setEMailSubject( emailsubject );
      props.setFileMatchingExpression( regex );
      props.setEMailBody( emailbody );
      props.setSpecialEMailBody( specialemailbody );
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

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
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
      
      String email = req.getParameter( "email" );
      if ( email != null && email.length() > 0 )
      {
        try
        {
          EMailSender.sendTestMessage( bbmonitor.logger );
        }
        catch (UnsupportedEncodingException | MessagingException ex)
        {
          bbmonitor.logger.error( "Unable to send test message", ex );
          out.println( "<p>Unable to send test message</p><pre>" );
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter( sw );
          ex.printStackTrace( pw );
          out.println( sw.toString() );
          out.println( "</pre>" );
        }
      }      
      
      out.println( "</body></html>" );
    }
    
  }
  
  
}
