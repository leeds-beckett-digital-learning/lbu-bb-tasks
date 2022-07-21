/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks;

import blackboard.platform.plugin.PlugInUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import uk.ac.leedsbeckett.bbtasks.tasks.TriggerAutoarchiveTask;

/**
 *
 * @author jon
 */
@WebServlet(name = "QuickyServlet", urlPatterns = {"/quicky/*"})
public class QuickyServlet  extends AbstractServlet
{


  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>" );
      out.println( "<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "h2, h3 { margin-top: 2em; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p>Method 'GET' not supported by this servlet.</p>" );
      out.println( "</body>" );
      out.println( "</html>" );
    }
  }
  

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
          throws ServletException, IOException
  {

    // Make sure that the user is authenticated and is a system admin.
    // Bail out if not.
    try
    {
      if ( !PlugInUtil.authorizeForSystemAdmin(req, resp) )
      {
        sendError( req, resp, "Admin authorization failed.");
        return;
      }
    }
    catch ( Exception e )
    {
      throw new ServletException( e );
    }
    
    String check = req.getParameter("check");
    if ( !"understand".equals( check ) )
    {
      sendError( req, resp, "Cannot procede because the check input does not contain the single word 'understand'.");
      return;
    }
    
    String triggerautoarchive = req.getParameter("triggerautoarchive");
    if ( triggerautoarchive != null && triggerautoarchive.length() > 0 )
    {
      doTriggerAutoarchives( req, resp );
      return;
    }
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>" );
      out.println( "<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "h2, h3 { margin-top: 2em; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p>.</p>" );
      out.println( "</body>" );
      out.println( "</html>" );
    }
    
    
  }

  protected void doTriggerAutoarchives(HttpServletRequest req, HttpServletResponse resp )
          throws ServletException, IOException
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
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Trigger Autoarchiving</h1>" );
      out.println( "<p>Immediate execution:</p>" );
      out.flush();
      
      String whitespacedelimiteddata = req.getParameter( "courseids" );
      String[] courseidarray = whitespacedelimiteddata.split( "[ \t\n\r,]+" );
      ArrayList<String> list = new ArrayList<>();
      
      int n=0;
      out.println( "<table>" );
      for ( String s : courseidarray )
      {
        s = s.trim();
        if ( s.startsWith( "\"" ) )
          s = s.substring( 1 );
        if ( s.endsWith( "\"" ) )
          s = s.substring( 0, s.length()-1 );
        
        if ( s.length() > 0 )
        {
          out.println( "<tr><td>" + (n++) + "</td><td>" + s + "</td></tr>" );
          list.add( s );
        }
      }
      out.println( "</table>" );
      
      long start = System.currentTimeMillis();
      try
      {
        TriggerAutoarchiveTask task = new TriggerAutoarchiveTask( list );
        task.setWebAppCore( webappcore );
        task.doTask();
      }
      catch ( Throwable ex )
      {
        webappcore.logger.error( "Exception while triggering auto-archive.", ex );
        out.print( "<p>Exception</p><pre>" );
        out.print( ex.getMessage() );
        out.println( "</pre>" );
      }
      float elapsed = (float)(System.currentTimeMillis() - start) / 1000.0f;
      
      out.println( "<p>Completed in " + elapsed + " seconds. Look in building block log for errors.</p>" );
      out.println( "</body></html>" );
    }
  }
  
}
