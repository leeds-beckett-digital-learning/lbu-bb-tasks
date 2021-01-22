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
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.text.StringEscapeUtils;

/**
 *
 * @author jon
 */
@WebServlet("/legacy/*")
public class LegacyFileServlet extends HttpServlet
{
  Path virtualserverbase=null;
  
  BBMonitor bbmonitor;
  
  Thread currenttask=null;  
  
  
  /**
   * Get a reference to the right instance of BBMonitor from an attribute which
   * that instance put in the servlet context.
  */
  @Override
  public void init() throws ServletException
  {
    super.init();
    bbmonitor = (BBMonitor)getServletContext().getAttribute( BBMonitor.ATTRIBUTE_CONTEXTBBMONITOR );
    // Work out where the 'virtual' server base is...
    ArrayList<File> candidates = new ArrayList<>();
    File vidir = new File( "/usr/local/bbcontent/vi/" );
    for ( File f : vidir.listFiles() )
    {
      if ( f.isDirectory() && f.getName().startsWith( "BB" ) )
        candidates.add( f );
    }
    if ( candidates.size() != 1 )
      throw new ServletException( "Cannot start legacy file servlet. There are " + candidates.size() + " virtual server directories in /usr/local/bbcontent/vi/" );
    virtualserverbase = Paths.get( candidates.get(0).getAbsolutePath() );
  }
  
  public void sendError( HttpServletRequest req, HttpServletResponse resp, String error ) throws ServletException, IOException
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
      out.println( "<body><p>" );
      out.println( error );
      out.println( "</p></body></html>" );
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
    
    
    String permanentlydelete = req.getParameter("permanentlydelete");
    if ( permanentlydelete != null && permanentlydelete.length() > 0 )
    {
      doGetTurnItInPermanentlyDelete( req, resp );
      return;
    }
    
    String prune = req.getParameter("prune");
    if ( prune != null && prune.length() > 0 )
    {
      doGetTurnItInPruning( req, resp );
      return;
    }
    
    String unprune = req.getParameter("unprune");
    if ( unprune != null && unprune.length() > 0 )
    {
      doGetTurnItInUnPruning( req, resp );
      return;
    }
    
    String turnitin = req.getParameter("turnitin");
    if ( turnitin != null && turnitin.length() > 0 )
    {
      doGetTurnItIn( req, resp );
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
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2, h3 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Legacy File Browser</h1>" );
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
      
      out.println( "<h3>Sub directories</h3>" );
      out.println( "<table>" );      
      int count=0;
      for ( File file : list )
      {
        if ( file.isDirectory() )
        {
          count ++;
          out.print( "<tr><td>" );
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
      
      out.println( "<h3>Files</h3>" );
      out.println( "<table>" );
      count=0;
      for ( File file : list )
      {
        if ( file.isFile() )
        {
          count++;
          out.print( "<tr>" );
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
          out.print( "</td>" );
          out.println( "</tr>" );        
        }
      }
      if ( count == 0 )
        out.println( "<tr><td>None</td></tr>" );
      out.println( "</table>" );
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
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        currenttask = new SearchThread( search );
        currenttask.start();
        out.println( "<h2>Search started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
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
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        currenttask = new TurnItInAnalysisThread();
        currenttask.start();
        out.println( "<h2>Analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
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
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        currenttask = new PruneThread();
        currenttask.start();
        out.println( "<h2>Pruning started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
            
      out.println( "</body></html>" );      
    }
  }

  protected void doGetTurnItInUnPruning(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        currenttask = new UnPruneThread();
        currenttask.start();
        out.println( "<h2>Unpruning started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
            
      out.println( "</body></html>" );      
    }
  }


  protected void doGetTurnItInPermanentlyDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
      out.println( "<h1>Turn It In - Permanently Delete Pruned Files</h1>" );
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is in progress.</p>" );
      }
      else
      {
        out.println( "<h2>Deleting</h2>" );
        Path tempstorepath    = virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
        if ( !Files.exists( tempstorepath ) )
        {
          out.println( "<p>There is no temporary copy directory to delete.</p>" );        
        }
        else
        {
          currenttask = new PermanentlyDeleteThread();
          currenttask.start();
          out.println( "<h2>Permanently delete task started</h2>" );
          out.println( "<p>Results will enter the log.</p>" );
        }
        
      }
            
      out.println( "</body></html>" );      
    }
  }
  
  class SearchThread extends Thread
  {
    File base = new File( "/usr/local" );
    String search;

    public SearchThread( String search )
    {
      this.search = search;
    }
    
    private void listDirectoryMatch( File dir )
    {
      bbmonitor.logger.info( "Searching in " + base.getAbsolutePath() );
      for ( File f : dir.listFiles() )
      {
        if ( f.getName().equals( search ) ) 
          bbmonitor.logger.info( f.getAbsolutePath() );
        if ( f.isDirectory() )
          listDirectoryMatch( f );
      }
    }
  
    @Override
    public void run()
    {
      try
      {
        listDirectoryMatch( base );
        bbmonitor.logger.info( "End of legacy file system search." );
      }
      finally
      {
        currenttask = null;
      }
    }    
  }


  class TurnItInAnalysisThread extends Thread
  {
    public TurnItInAnalysisThread()
    {
    }
  
    @Override
    public void run()
    {
      try
      {
        Path coursebase = virtualserverbase.resolve( "courses/1/" );
        long totalgood = 0L;
        long totalbad = 0L;

        ArrayList<Path> coursepaths = new ArrayList<>();
        ArrayList<Path> filepaths = new ArrayList<>();
        try ( Stream<Path> stream = Files.list(coursebase); )
        {
          stream.forEach( new Consumer<Path>(){public void accept( Path f ) {coursepaths.add(f);}});
        }
        for ( int i=0; i<coursepaths.size(); i++ )
        {
          Path f = coursepaths.get(i);
          if ( Files.isDirectory(f) )
          {
            String courseid = f.getFileName().toString();
            Path uploads = f.resolve( "ppg/BB_Direct/Uploads/" );
            if ( Files.exists( uploads ) && Files.isDirectory( uploads ) )
            {
              bbmonitor.logger.info( "Checking redundant tii files in " + i + " of " + coursepaths.size() + " " + uploads.toString() );
              filepaths.clear();
              try ( Stream<Path> stream = Files.list( uploads ); )
              {
                stream.forEach( new Consumer<Path>(){public void accept( Path f ) {filepaths.add(f);}});
              }

              for ( Path uploadedfile : filepaths )
              {
                if ( Files.isRegularFile( uploadedfile ) )
                {
                  Path lastpart = uploadedfile.getName( uploadedfile.getNameCount()-1 );
                  String name = lastpart.toString();
                  if ( !name.startsWith( courseid+"_" ) )
                    totalgood += Files.size( uploadedfile );
                  else
                    totalbad += Files.size( uploadedfile );
                }
              }
            }
          }
        }      


        bbmonitor.logger.info( "Bytes of data that belong to the module = "        + totalgood );
        bbmonitor.logger.info( "Bytes of data that DO NOT belong to the module = " + totalbad  );
        bbmonitor.logger.info( "End of report."                                                );
      }
      catch ( Exception ex )
      {
        bbmonitor.logger.error( "Error attempting to analyse turnitin files." );
        bbmonitor.logger.error(ex);
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }

  class PruneThread extends Thread
  {
    public PruneThread()
    {
    }
  
    @Override
    public void run()
    {
      try
      {
        long start = System.currentTimeMillis();
        bbmonitor.logger.info( "Turn It In pruning process started." );
        int filesmoved = 0;

        Path coursebase = virtualserverbase.resolve( "courses/1" );
        BigInteger totalgood = BigInteger.ZERO;
        BigInteger totalbad = BigInteger.ZERO;

        // Prep...
        Path tempstorepath    = virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
        Path coursetargetpath = tempstorepath.resolve( "1" );
        try
        {
          if ( !Files.exists( tempstorepath ) )
            Files.createDirectory( tempstorepath    );
          if ( !Files.exists( coursetargetpath ) )
            Files.createDirectory( coursetargetpath );

          ArrayList<Path> coursepaths = new ArrayList<>();
          ArrayList<Path> filepaths = new ArrayList<>();
          // get a list in a way that autocloses the directory
          try ( Stream<Path> stream = Files.list(coursebase); )
          {
            stream.forEach( new Consumer<Path>(){public void accept( Path f ) {coursepaths.add(f);}});
          }

          for ( int i=0; i<coursepaths.size(); i++ )
          {     
            Path f = coursepaths.get(i);
            filepaths.clear();
            String courseid = f.getFileName().toString();
            Path uploadssource = f.resolve("ppg").resolve("BB_Direct").resolve("Uploads");
            bbmonitor.logger.info( "Working on: " + i + " of " + coursepaths.size() + " = " + uploadssource.toString() );
            if ( Files.exists( uploadssource ) && Files.isDirectory( uploadssource ) )
            {
              Path target = tempstorepath.resolve( courseid );
              bbmonitor.logger.info( "Target directory: " + target.toString() );
              if ( !Files.exists(target) )
                Files.createDirectory( target );
              // get a list in a way that autocloses the directory
              try ( Stream<Path> stream = Files.list(uploadssource); )
              {
                stream.forEach( new Consumer<Path>(){public void accept( Path f ) {filepaths.add(f);}});
              }

              for ( Path uploadedfile : filepaths )
              {
                if ( Files.isRegularFile( uploadedfile ) )
                {
                  Path lastpart = uploadedfile.getName( uploadedfile.getNameCount()-1 );
                  String name = lastpart.toString();
                  if ( !name.startsWith( courseid+"_" ) )
                  {
                    //bbmonitor.logger.info( "This file should be moved : " + uploadedfile.toString() );
                    Path targetfile = target.resolve( name );
                    //bbmonitor.logger.info( "To here: " + targetfile.toString() );
                    if ( uploadedfile.getFileSystem() == targetfile.getFileSystem() )
                    {
                      //bbmonitor.logger.info( "Same file system." );
                      if ( Files.exists( targetfile ) )
                      {
                        bbmonitor.logger.info( "Target already exists." + targetfile.toString() );
                        if ( !(Files.size( uploadedfile ) != Files.size(targetfile)) )
                          bbmonitor.logger.info( "Target IS THE WRONG SIZE." );
                      }
                      Files.move( uploadedfile, targetfile );
                      filesmoved++;
                    }
                  }
                }
              }
            }
          }
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to prune turnitin files." );
          bbmonitor.logger.error(ex);
        }

        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        bbmonitor.logger.info( "Turn It In pruning process ended after " + elapsed + " seconds. " + filesmoved + " files moved." ); 
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }  


  class UnPruneThread extends Thread
  {
    public UnPruneThread()
    {
    }
  
    @Override
    public void run()
    {
      try
      {
        long start = System.currentTimeMillis();
        bbmonitor.logger.info( "Turn It In Unpruning process started." );
        int filesmoved = 0;

        Path coursebase = virtualserverbase.resolve( "courses/1" );
        BigInteger totalgood = BigInteger.ZERO;
        BigInteger totalbad = BigInteger.ZERO;

        // Prep...
        Path tempstorepath    = virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
        Path coursetargetpath = tempstorepath.resolve( "1" );
        try
        {
          ArrayList<Path> coursepaths = new ArrayList<>();
          ArrayList<Path> backupfilepaths = new ArrayList<>();
          // get a list in a way that autocloses the directory
          try ( Stream<Path> stream = Files.list(coursebase); )
          {
            stream.forEach( new Consumer<Path>(){public void accept( Path f ) {coursepaths.add(f);}});
          }

          for ( int i=0; i<coursepaths.size(); i++ )
          {     
            Path f = coursepaths.get(i);
            backupfilepaths.clear();
            String courseid = f.getFileName().toString();
            Path uploadsdir = f.resolve("ppg").resolve("BB_Direct").resolve("Uploads");
            bbmonitor.logger.info( "Working on: " + i + " of " + coursepaths.size() + " = " + uploadsdir.toString() );
            if ( Files.exists( uploadsdir ) && Files.isDirectory( uploadsdir ) )
            {
              Path backupdir = tempstorepath.resolve( courseid );
              //bbmonitor.logger.info( "Backup directory: " + backupdir.toString() );
              if ( !Files.exists(backupdir) )
                continue;
              
              // get a list in a way that autocloses the directory
              try ( Stream<Path> stream = Files.list(backupdir); )
              {
                stream.forEach( new Consumer<Path>(){public void accept( Path f ) {backupfilepaths.add(f);}});
              }

              for ( Path backupfile : backupfilepaths )
              {
                if ( Files.isRegularFile( backupfile ) )
                {
                  Path lastpart = backupfile.getName( backupfile.getNameCount()-1 );
                  String name = lastpart.toString();
                  //bbmonitor.logger.info( "This file should be moved : " + backupfile.toString() );
                  Path targetfile = uploadsdir.resolve( name );
                  //bbmonitor.logger.info( "To here: " + targetfile.toString() );
                  if ( backupfile.getFileSystem() == targetfile.getFileSystem() )
                  {
                    if ( Files.exists( targetfile ) )
                    {
                      bbmonitor.logger.info( "Target already exists." + targetfile.toString() );
                      if ( !(Files.size( backupfile ) != Files.size(targetfile)) )
                        bbmonitor.logger.info( "Target IS THE WRONG SIZE." );
                    }
                    else
                    {
                      Files.move( backupfile, targetfile );
                      filesmoved++;
                    }
                  }
                }
              }
            }
          }
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error while unpruning." );
          bbmonitor.logger.error(ex);
        }

        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        bbmonitor.logger.info( "Turn It In pruning process ended after " + elapsed + " seconds. " + filesmoved + " files moved." ); 
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }  

  class PermanentlyDeleteThread extends Thread
  {
    public PermanentlyDeleteThread()
    {
    }
  
    @Override
    public void run()
    {
      try
      {
        bbmonitor.logger.info( "Turn It In permanently deleting process started. May take many minutes. " ); 
        long start = System.currentTimeMillis();
        Path tempstorepath    = virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
        try
        {
          Files.walkFileTree(tempstorepath, new SimpleFileVisitor<Path>() {
             @Override
             public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                 Files.delete(file);
                 return FileVisitResult.CONTINUE;
             }

             @Override
             public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                 Files.delete(dir);
                 return FileVisitResult.CONTINUE;
             }
          });
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error while permanently deleting." );
          bbmonitor.logger.error(ex);
        }

        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        bbmonitor.logger.info( "Turn It In permanently deleting process ended after " + elapsed + " seconds. " ); 
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }  

}