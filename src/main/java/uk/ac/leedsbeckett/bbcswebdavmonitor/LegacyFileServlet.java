/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.text.StringEscapeUtils;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.DemoTask;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.LegacyBucketAnalysisTask;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.LegacySearchTask;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.LegacyTurnitinAnalysisTask;
import uk.ac.leedsbeckett.bbcswebdavmonitor.tasks.LegacyTurnitinPruneTask;

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

    boolean isMultipart = ServletFileUpload.isMultipartContent( req );
    if ( isMultipart ) 
    {
      doBrowserUpload( req, resp );
      return;
    }

    String browser = req.getParameter("browser");
    if ( browser!= null && browser.length()> 0 )
    {
      doBrowserPost( req, resp );
      return;
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

  
  protected void doBrowserPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    resp.setContentType("text/html");
    Enumeration<String> pnames = req.getParameterNames();
    
    
    
    ArrayList<String> filenames = new ArrayList<>();
    ArrayList<String> parameternames = new ArrayList<>();
    while ( pnames.hasMoreElements() )
    {
      String name = pnames.nextElement();
      parameternames.add(name);
      if ( name.startsWith( "file_select_" ) )
        filenames.add( URLDecoder.decode( name.substring( "file_select_".length() ), "UTF-8" ) );
    }
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>" );
      out.println( "<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "h2, h3 { margin-top: 2em; }" );
      out.println( "td { padding-right: 2em; }" );
      out.println( ".bookmarks {  background-color: rgb(220,220,220); padding: 0.5em 1em 0.5em 1em; border: thin black solid; max-width: 20em; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Legacy File Browser UPLOAD</h1>" );
      out.println( "<h2>Parameters</h2>" );
      out.println( "<pre><tt>" );
      for ( String parametername : parameternames )
        out.println( parametername + " = " + req.getParameter(parametername) );
      out.println( "</tt></pre>" );
//      out.println( "<h2>Selected Files</h2>" );
//      out.println( "<pre><tt>" );
//      for ( String filename : filenames )
//        out.println( filename );
//      out.println( "</tt></pre>" );
      
      String p = req.getParameter( "submitdelete" );
      if ( p!= null && p.length()>0 )
      {
        out.println( "<h2>Deleting files</h2>" );
        for ( String filename : filenames )
        {
          out.println( "<h3>Deleting " + filename + "</h3>" );
          File f = new File( filename );
          if ( f.exists() )
          {
            if ( !f.isFile() )
              out.println( "<p>Not a file.</p>" );            
            else
            {
              try
              {
                Files.delete( f.toPath() );
              }
              catch ( IOException ioex )
              {
                out.println( "<p>Technical fault attempting to delete.</p>" );                          
                bbmonitor.logger.error( "Unable to delete " + f.getPath(), ioex );
              }
            }
          }
          else
            out.println( "<p>File doesn't exist.</p>" );
        }
      }
      else
      {
        out.println( "<p>Unknown or unimplemented file operation.</p>" );        
      }
      
      
      out.print(   "<p><a href=\"?path=" );
      out.print(   req.getParameter( "path" ) );
      out.println( "\">Back</a></p>" );
      out.println( "</body></html>" );      
    }
  }
  
  
  protected void doBrowserUpload(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    resp.setContentType("text/html");
    // Create a factory for disk-based file items
    DiskFileItemFactory factory = new DiskFileItemFactory();
    // Configure a repository (to ensure a secure temp location is used)
    ServletContext servletContext = this.getServletConfig().getServletContext();
    File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
    factory.setRepository(repository);
    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload(factory);
    
    
    
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>" );
      out.println( "<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "h2, h3 { margin-top: 2em; }" );
      out.println( "td { padding-right: 2em; }" );
      out.println( ".bookmarks {  background-color: rgb(220,220,220); padding: 0.5em 1em 0.5em 1em; border: thin black solid; max-width: 20em; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Legacy File Browser</h1>" );

      String path=null;
      try
      {    
        // Parse the request
        List<FileItem> items = upload.parseRequest(req);
        // Process the uploaded items
        Iterator<FileItem> iter = items.iterator();
        while (iter.hasNext())
        {
          FileItem item = iter.next();
          if ( item.isFormField() )
          {
            String name = item.getFieldName();
            String value = item.getString();
            if ( "path".equals( name ) )
              path = URLDecoder.decode( value, "UTF-8" );
          }
        }        
        if ( path == null )
        {
          out.println( "<p>Unknown destination path.</p>" );
        }
        else
        {
          out.println( "<p>" + path + "</p>" );
          iter = items.iterator();
          while (iter.hasNext())
          {
            FileItem item = iter.next();
            if ( !item.isFormField() )
            {
              out.println( "<p>" + item.getName() + "</p>" );
              File fpath = new File( path );
              File f     = new File( fpath, item.getName() );
              out.println( "<p>" + f.getPath() + "</p>" );
              item.write(f);
            }
          }
        }
      }
      catch (Exception ex)
      {
        Logger.getLogger(LegacyFileServlet.class.getName()).log(Level.SEVERE, null, ex);
      }
      
            
      out.print( "<p><a href=\"?path=" );
      out.print( path );
      out.println( "\">Back</a></p>" );
      out.println( "</body></html>" );      
    }
  }
  
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    String download = req.getParameter("download");
    if ( download != null && download.length() > 0 )
    {
      doGetDownload( req, resp );
      return;
    }

    String path = req.getParameter("path");
    if ( path == null || path.length() == 0 )
      path = "/usr/local/";
    
    String[] parts = path.split("/");
    
    File base = new File( path );
    
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>" );
      out.println( "<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "h2, h3 { margin-top: 2em; }" );
      out.println( "td { padding-right: 2em; }" );
      out.println( ".bookmarks {  background-color: rgb(220,220,220); padding: 0.5em 1em 0.5em 1em; border: thin black solid; max-width: 20em; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Legacy File Browser</h1>" );
      out.println( "<h2>Bookmarks</h2>" );
      out.println( "<div class=\"bookmarks\">" );
      out.println( "<ul>" );
      out.print( "<li><a href=\"?path=" );
      out.print( URLEncoder.encode( bbmonitor.pluginbase.toString(), "UTF-8" ) );
      out.println( "\">This building block's base folder.</a> (Includes the configuration files.)</li>" );
      out.print( "<li><a href=\"?path=" );
      out.print( URLEncoder.encode( bbmonitor.logbase.toString(), "UTF-8" ) );
      out.println( "\">This building block's log folder.</a></li>" );
      out.print( "<li><a href=\"?path=" );
      out.print( URLEncoder.encode( bbmonitor.virtualserverbase.toString(), "UTF-8" ) );
      out.println( "\">The virtual server base folder.</a></li>" );
      out.println( "</ul>" );
      out.println( "</div>" );
      out.print( "<h2>" );
      String rebuild = "";
      out.print( "" );
      for ( int i=0; i<parts.length; i++ )
      {
        rebuild = rebuild + parts[i] + "/";
        if ( i>1 )
        {
          out.print( "<a href=\"?path=" );
          out.print( URLEncoder.encode( rebuild, "UTF-8" ) );
          out.print( "\">" );
        }
        out.print( parts[i] );
        if ( i>1 )
          out.print( "</a>" );
        out.print( "/" );
      }
      out.println( "</h2>" );
      
      File[] list = base.listFiles();
      Arrays.sort(list);
      
      out.println( "<div style=\"margin-left: 3em;\">" );
      out.println( "<h3>Sub directories</h3>" );
      out.println( "<div style=\"margin-left: 3em;\">" );
      out.println( "<table>" );      
      int count=0;
      for ( File file : list )
      {
        if ( file.isDirectory() )
        {
          count ++;
          out.print( "<tr>" );
          out.print( "<td>" );
          out.print( "<a href=\"?path=" );
          out.print( URLEncoder.encode( file.getAbsolutePath(), "UTF-8" ) );
          out.print( "\">" );
          out.print( StringEscapeUtils.escapeHtml4( file.getName() ) );
          out.print( "</a>" );        
          out.println( "</td></tr>" );        
        }
      }
      if ( count == 0 )
        out.println( "<tr><td>None</td></tr>" );
      out.println( "</table>" );
      out.println( "<h4>Create Directory Here</h4>" );
      out.println( "<div>" );      
      out.println( "<form method=\"POST\" action=\".\">" );
      out.println( "<input type=\"hidden\" name=\"browser\" value=\"yes\"/>" );      
      out.print(   "<input type=\"hidden\" name=\"path\" value=\"" );
      out.print(   URLEncoder.encode( path, "UTF-8" ) );
      out.println( "\"/>" );
      out.println( "<input name=\"dirname\"/>" );
      out.println( "<input type=\"submit\" name=\"submit\" value=\"Create Directory\"/>" );
      out.println( "</form>" );
      out.println( "</div>" );
      out.println( "</div>" );
      
      out.println( "<h3>Files</h3>" );
      out.println( "<div style=\"margin-left: 3em;\">" );
      out.println( "<form method=\"POST\" action=\".\">" );
      out.println( "<input type=\"hidden\" name=\"browser\" value=\"yes\"/>" );
      out.print(   "<input type=\"hidden\" name=\"path\" value=\"" );
      out.print(   URLEncoder.encode( path, "UTF-8" ) );
      out.println( "\"/>" );
      out.println( "<table>" );
      count=0;
      for ( File file : list )
      {
        if ( file.isFile() )
        {
          count++;
          out.print( "<tr>" );
          out.print( "<td><input type=\"checkbox\" name=\"file_select_" );
          out.print( URLEncoder.encode( file.getAbsolutePath(), "UTF-8" ) );
          out.print( "\"/></td>" );
          out.print( "<td>" );
          out.print( "<a href=\"" );
          out.print( URLEncoder.encode( file.getName(), "UTF-8" ) );
          out.print( "?download=" );
          out.print( URLEncoder.encode( file.getAbsolutePath(), "UTF-8" ) );
          out.print( "\">" );
          out.print( StringEscapeUtils.escapeHtml4( file.getName() ) );
          out.print( "</a>" );        
          out.println( "</td>" );
          out.print( "<td>" );
          out.print( file.length() );
          out.println( "</td>" );
          out.print( "<td>" );
          out.print( bbmonitor.dateformat.format( new Date( file.lastModified() ) ) );
          out.print( "</td>" );
          out.println( "</tr>" );        
        }
      }
      if ( count == 0 )
        out.println( "<tr><td>None</td></tr>" );
      out.println( "</table>" );
      out.println( "<input type=\"submit\" name=\"submitdelete\" value=\"Delete\"/>" );
      out.println( "</form>" );
      out.println( "</div>" );
      out.println( "</div>" );
      out.println( "<h4>Upload File Here</h4>" );
      out.println( "<div>" );      
      out.println( "<form method=\"POST\" enctype=\"multipart/form-data\" action=\".\">" );
      out.print(   "<input type=\"hidden\" name=\"path\" value=\"" );
      out.print(   URLEncoder.encode( path, "UTF-8" ) );
      out.println( "\"/>" );
      out.println( "<input type=\"file\" name=\"fileupload\"/>" );
      out.println( "<input type=\"submit\" name=\"submit\" value=\"Upload File\"/>" );
      out.println( "</form>" );
      out.println( "</div>" );
      out.println( "</body></html>" );      
    }
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
        servercoordinator.requestTask( new DemoTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        bbmonitor.logger.error( "Error attempting to request the task.", e );
      }
      
      out.println( "</body></html>" );      
    }
  }
  
  
  
  protected void doGetDownload(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    String download = req.getParameter( "download" );
    
    File f = new File( download );
    if ( !f.exists() || !f.isFile() )
      throw new IOException( "File does not exist." );
    
    resp.setContentType("application/binary");
    try ( ServletOutputStream out = resp.getOutputStream(); FileInputStream in = new FileInputStream( f ) )
    {
      in.transferTo(out);
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
        servercoordinator.requestTask( new LegacySearchTask( search ) );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        bbmonitor.logger.error( "Error attempting to request the task.", e );
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
        servercoordinator.requestTask( new LegacyBucketAnalysisTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        bbmonitor.logger.error( "Error attempting to request the task.", e );
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
        servercoordinator.requestTask( new LegacyTurnitinAnalysisTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        bbmonitor.logger.error( "Error attempting to request the task.", e );
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
        servercoordinator.requestTask( new LegacyTurnitinPruneTask() );
        out.println( "<p>Successfully requested task.</p>" );
      }
      catch ( Exception e )
      {
        out.println( "<p>Error attempting to request the task.</p>" );        
        bbmonitor.logger.error( "Error attempting to request the task.", e );
      }      
      out.println( "</body></html>" );      
    }
  }

}
