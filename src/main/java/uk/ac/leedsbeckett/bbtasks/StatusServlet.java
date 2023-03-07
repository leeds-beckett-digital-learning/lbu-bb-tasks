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

package uk.ac.leedsbeckett.bbtasks;

import blackboard.platform.plugin.PlugInUtil;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Level;
import uk.ac.leedsbeckett.bbtasks.WebAppCore.TaskListEntry;
import uk.ac.leedsbeckett.bbtasks.messaging.RequestTaskListMessage;

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
    BuildingBlockProperties props = webappcore.getProperties();
    
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
      out.println( "<h1>Tasks - Status Page</h1>" );
      
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
      webappcore.clearTaskListEntries();
      webappcore.sendMessage( new RequestTaskListMessage() );
      out.println( "<p>Successfully requested task lists.</p>" );
    }
    catch ( Exception e )
    {
      out.println( "<p>Error attempting to request the task.</p></body></html>" );        
      webappcore.logger.error( "Error attempting to request the task.", e );
      return;
    }    

    out.flush();
    
    try { Thread.sleep( 5000 ); } catch (InterruptedException ex) {}
    
    for ( TaskListEntry tle : webappcore.getTaskListEntries() )
    {
      out.println( "<h4>" + tle.name + "</h4>" );
      out.print( "<p><em>" );
      out.print( tle.timestamp );
      out.print( "</em></p>" );
      out.print( "<p><pre>" );
      out.print( tle.list );
      out.println( "</pre></p>" );
    }
    
    out.println( "</body></html>" );
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
    out.println( webappcore.getBootstrapLog() );
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
    Level[] levellist = { Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG };
    Level currentlevel = props.getLogLevel();
    String currentemailname = props.getEmailName();
    String currentemailaddress = props.getEmailAddress();
    
    out.println( "<h2>Configure Settings</h2>" );
    out.println( "<p>Note: if you want to stop this building block plugin ");
    out.println( "running you should use the Building Blocks link in the ");
    out.println( "Integrations panel of the System Administration page.</p>" );
    
    out.println( "<form name=\"config\" action=\"status\" method=\"GET\">" );
    out.println( "<input type=\"hidden\" name=\"setupsave\" value=\"true\"/>" );

    out.println( "<h3>Contact Email Address</h3>" );
    out.println( "<p>Provide the email address that will be used in the 'from' field of outgoing email.</p>" );
    out.println( "<input name=\"emailaddress\" value=\"" + currentemailaddress + "\"/>" );
    out.println( "<p>Provide the friendly name corresponding to that address.</p>" );
    out.println( "<input name=\"emailname\" value=\"" + currentemailname + "\"/>" );
    out.println( "<h3>Technical Log</h3>" );
    out.println( "<p>How much detail do you want in the technical logs?</p>" );
    out.println( "<table><tr><td>Main Log<br/>" );
    out.println( "<select name=\"loglevel\" size=\"4\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (currentlevel.equals(level)?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );
    out.println( "</td></tr></table>" );
    
    out.println( "<h3>Submit</h3>" );
    out.println( "<p><input type=\"submit\" value=\"Save\"/></p>" );
    out.println( "</form>" );
  }

  void sendSetupSave( HttpServletRequest req, ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String loglevel         = req.getParameter( "loglevel"      );
    String emailaddress     = req.getParameter( "emailaddress"  );
    String emailname        = req.getParameter( "emailname"     );
    out.println( "<h2>Saving Configuration Settings</h2>" );

    try
    {
      props.setLogLevel(   Level.toLevel(  loglevel ) );
      props.setEmailAddress(emailaddress);
      props.setEmailName(emailname);
      webappcore.saveProperties();
    }
    catch ( Throwable th )
    {
      webappcore.logger.error( "Unable to save properties.", th );
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
      out.println( "<h1>LBU BB Tasks</h1>" );
            
      out.println( "</body></html>" );
    }
    
  }
  
  
}
