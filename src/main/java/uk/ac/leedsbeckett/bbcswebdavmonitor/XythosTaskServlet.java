/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import com.xythos.common.api.NetworkAddress;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.Directory;
import com.xythos.fileSystem.Revision;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 *
 * @author jon
 */
@WebServlet("/xythos/*")
public class XythosTaskServlet extends AbstractServlet
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
    
    
    String permanentlydelete = req.getParameter("analyseautoarchives");
    if ( permanentlydelete != null && permanentlydelete.length() > 0 )
    {
      doAnalyseAutoArchives( req, resp );
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

  protected void doAnalyseAutoArchives(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Xythos Servlet</h1>" );
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        currenttask = new AnalyseAutoArchiveThread( vs );
        currenttask.start();
        out.println( "<h2>Autoarchive analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }
  
  class AnalyseAutoArchiveThread extends Thread
  {
    VirtualServer vs;
    
    public AnalyseAutoArchiveThread( VirtualServer vs )
    {
      this.vs = vs;
    }
  
    
    private long analyseZip( com.xythos.fileSystem.File zip ) throws XythosException, IOException
    {
      long runningtotal = 0L;
      
      bbmonitor.logger.info( zip.getName() );
      bbmonitor.logger.info( zip.getEntrySize() );
      XythosAdapterChannel channel = null;
      ZipFile zipfile = null;
      try
      {
        Revision r = zip.getLatestRevision();
        bbmonitor.logger.info( "Revision" );
        channel = new XythosAdapterChannel( r );
        //channel.setLogger( bbmonitor.logger );
        bbmonitor.logger.info( "Channel" );
        zipfile = new ZipFile( channel );
        bbmonitor.logger.info( "Zip file" );
        Enumeration<ZipArchiveEntry> e = zipfile.getEntries();
        bbmonitor.logger.info( "Entries" );
        while ( e.hasMoreElements() )
        {
          ZipArchiveEntry entry = e.nextElement();
          String name = entry.getName();
          bbmonitor.logger.info( "Name = " + name );
          if ( name.startsWith( "ppg/BB_Direct/Uploads/" ))
            runningtotal += entry.getSize();
        }
      }
      finally
      {
        bbmonitor.logger.info( "Closing" );
        if ( zipfile != null )
          zipfile.close();
        if ( channel != null )
          channel.close();
      }
      bbmonitor.logger.info( "------------------------------------" );
      return runningtotal;
    }
    
    
    @Override
    public void run()
    {
      Path logfile = bbmonitor.logbase.resolve( "autoarchiveanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      long runningtotal = 0L;

      try
      {
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to analyse autoarchives. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to analyse autoarchives.", ex);
          return;
        }
        
        bbmonitor.logger.info( "Analyse autoarchives process started. May take many minutes. " ); 
        long start = System.currentTimeMillis();

        Context context = null;
        FileSystemEntry entry = null;
        StringBuilder message = new StringBuilder();
        try
        {
          context = AdminUtil.getContextForAdmin("MultipleAppServerProgram:150");
          entry = FileSystem.findEntry( vs, "/internal/autoArchive", false, context );
          if ( entry == null )
          {
            message.append( "Could not find entry.\n" );
          }
          else
          {
            //message.append( "Entry " + entry.getName() + " java class = " + entry.getClass().getCanonicalName() + "\n" );
            if ( entry instanceof Directory )
            {
              Directory dir = (Directory)entry;
              FileSystemEntry[] entries = dir.getDirectoryContents(true);
              for ( FileSystemEntry f : entries )
              {
                if ( !(f instanceof com.xythos.fileSystem.File) )
                  continue;
                if ( !"application/zip".equals( f.getFileContentType() ) )
                  continue;
                runningtotal += analyseZip( (com.xythos.fileSystem.File)f );
              }
            }
          }
        }
        catch ( Throwable th )
        {
          bbmonitor.logger.error( "Error occured while running analysis of autoarchives.", th);
        }
        
        message.append( "Found " + runningtotal + " bytes of Turnitin uploads in the autoarchives." );
        
        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( message );
          log.println( "Analyse autoarchives process ended after " + elapsed + " seconds. "      );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to analyse autoarchives.", ex);
        }
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }  
  
}
