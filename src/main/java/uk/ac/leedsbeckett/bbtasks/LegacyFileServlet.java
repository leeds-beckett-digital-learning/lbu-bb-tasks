/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

import blackboard.platform.plugin.PlugInUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uk.ac.leedsbeckett.bbtasks.tasks.DemoTask;
import uk.ac.leedsbeckett.bbtasks.tasks.LegacyBucketAnalysisTask;
import uk.ac.leedsbeckett.bbtasks.tasks.LegacySearchTask;
import uk.ac.leedsbeckett.bbtasks.tasks.LegacyTurnitinAnalysisTask;
import uk.ac.leedsbeckett.bbtasks.tasks.LegacyTurnitinPruneTask;
import uk.ac.leedsbeckett.bbtasks.tasks.PeerServiceTask;

/**
 *
 * @author jon
 */
@WebServlet("/legacy/*")
public class LegacyFileServlet extends AbstractServlet
{
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
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
    
    
    String demotask = req.getParameter("demotask");
    if ( demotask != null && demotask.length() > 0 )
    {
      doGetDemoTask( req, resp );
      return;
    }
    
    String peerservice = req.getParameter("peerservice");
    if ( peerservice != null && peerservice.length() > 0 )
    {
      doGetPeerServiceTask( req, resp );
      return;
    }
    
    String prune = req.getParameter("prune");
    if ( prune != null && prune.length() > 0 )
    {
      doGetTurnItInPruning( req, resp );
      return;
    }
    
    String turnitin = req.getParameter("turnitin");
    if ( turnitin != null && turnitin.length() > 0 )
    {
      doGetTurnItIn( req, resp );
      return;
    }
        
    String analysis = req.getParameter("analysis");
    if ( analysis != null && analysis.length() > 0 )
    {
      doGetLegacyAnalysis( req, resp );
      return;
    }
        
    String search = req.getParameter("search");
    if ( search != null && search.length() > 0 )
    {
      doGetSearch( req, resp );
      return;
    }

    sendError( req, resp, "Unknown web address.");
  }  
  
 

  protected void doGetDemoTask(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Start Demo Task</h1>" );
      
      try
      {
        webappcore.requestTask( new DemoTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }
      
      out.println( "</body></html>" );      
    }
  }
  
  protected void doGetPeerServiceTask(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Start Peer Service Task</h1>" );
      
      try
      {
        webappcore.requestTask( new PeerServiceTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }
      
      out.println( "</body></html>" );      
    }    
  }
  
  protected void doGetSearch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    String search = req.getParameter("search");
    
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
      out.println( "<h1>Legacy File Browser</h1>" );
      try
      {
        webappcore.requestTask( new LegacySearchTask( search ) );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }       
      out.println( "</body></html>" );      
    }
  }


  protected void doGetLegacyAnalysis(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Legacy Files Analysis</h1>" );
      try
      {
        webappcore.requestTask( new LegacyBucketAnalysisTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }              
      out.println( "</body></html>" );      
    }
  }


  protected void doGetTurnItIn(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Turn It In Analysis</h1>" );
      try
      {
        webappcore.requestTask( new LegacyTurnitinAnalysisTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }      
      out.println( "</body></html>" );      
    }
  }

  protected void doGetTurnItInPruning(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Turn It In Pruning</h1>" );
      try
      {
        webappcore.requestTask( new LegacyTurnitinPruneTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        webappcore.logger.error( "Error attempting to request the task.", e );
      }      
      out.println( "</body></html>" );      
    }
  }

}
