/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.text.StringEscapeUtils;

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
      out.println( "</div>" );
      
      out.println( "<h3>Files</h3>" );
      out.println( "<div style=\"margin-left: 3em;\">" );
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
          out.println( "</td>" );
          out.print( "<td>" );
          out.print( dateformat.format( new Date( file.lastModified() ) ) );
          out.print( "</td>" );
          out.println( "</tr>" );        
        }
      }
      if ( count == 0 )
        out.println( "<tr><td>None</td></tr>" );
      out.println( "</table>" );
      out.println( "</div>" );
      out.println( "</div>" );
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
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        currenttask = new LegacyFileAnalysisThread();
        currenttask.start();
        out.println( "<h2>Analysis started</h2>" );
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
        Path tempstorepath    = bbmonitor.virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
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

  class SubBucket
  {
    String name;
    int filecount;
    long filesize;
  }
  
  class AnalysisBucket
  {
    String name;
    int filecount;
    long filesize;
    ArrayList<SubBucket> dirs = new ArrayList<>();
  }

  class BucketMap extends HashMap<String,AnalysisBucket>
  {
    public AnalysisBucket get(Object key)
    {
      AnalysisBucket b = super.get(key);
      if ( b == null )
      {
        b = new AnalysisBucket();
        b.name = key.toString();
        put( b.name, b );
      }
      return b;
    }    
  }
  
  class LegacyFileAnalysisThread extends Thread
  {
    public LegacyFileAnalysisThread()
    {
    }

    void analyseDirectory( AnalysisBucket bucket, Path dir ) throws IOException
    {
      boolean empty=true;
      ArrayList<Path> filepaths = new ArrayList<>();
      SubBucket subbucket = new SubBucket();
      subbucket.name = dir.toString();
      bucket.dirs.add(subbucket);

      // Look at all regular files under this directory to maximum depth
      try ( Stream<Path> stream = Files.find( dir, Integer.MAX_VALUE, (p, atts)->Files.isRegularFile(p) ); )
      {
        stream.forEach( new Consumer<Path>(){public void accept( Path f ) {filepaths.add(f);}});
      }
      for ( Path p : filepaths )
      {
        empty=false;
        subbucket.filecount++;
        subbucket.filesize += Files.size(p);
      }
      
      bucket.filecount += subbucket.filecount;
      bucket.filesize += subbucket.filesize;
    }
    
    @Override
    public void run()
    {
      BucketMap bucketmap = new BucketMap();
      Path coursebase = bbmonitor.virtualserverbase.resolve( "courses/1/" );
      Path logfile = bbmonitor.logbase.resolve( "legacyfilesanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      
      try
      {

        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to analyse legacy file system. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to analyse legacy files.", ex);
          return;
        }

        try
        {
          ArrayList<Path> coursepaths = new ArrayList<>();
          ArrayList<Path> bucketpaths = new ArrayList<>();
          ArrayList<Path> ppgbucketpaths = new ArrayList<>();
          try ( Stream<Path> stream = Files.list(coursebase); )
          {
            stream.forEach( new Consumer<Path>(){public void accept( Path f ) {coursepaths.add(f);}});
          }
          for ( int i=0; i<coursepaths.size(); i++ )
          {
            Path f = coursepaths.get(i);
            if ( !Files.isDirectory(f) )
              continue;

            bbmonitor.logger.info( "Checking legacy file system " + i + " of " + coursepaths.size() + " " + f.toString() );

            bucketpaths.clear();
            ppgbucketpaths.clear();
            try ( Stream<Path> stream = Files.list( f ); )
            {
              stream.forEach( new Consumer<Path>(){
                public void accept( Path f )
                {
                  if( Files.isDirectory(f) && !f.endsWith("ppg") )
                    bucketpaths.add(f);
                }});
            }
            Path ppg = f.resolve( "ppg" );
            if ( Files.exists(ppg) )
            {
              try ( Stream<Path> stream = Files.list( ppg ); )
              {
                stream.forEach( new Consumer<Path>(){
                  public void accept( Path f )
                  {
                    if( Files.isDirectory(f) )
                      ppgbucketpaths.add(f);
                  }});
              }            
            }

            for ( Path p : bucketpaths )
            {
              AnalysisBucket b = bucketmap.get( p.getFileName().toString() );
              analyseDirectory( b, p );
            }
            for ( Path p : ppgbucketpaths )
            {
              AnalysisBucket b = bucketmap.get( "ppg/" + p.getFileName().toString() );
              analyseDirectory( b, p );
            }
          }      
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to analyse legacy files.", ex);
          return;
        }

        AnalysisBucket[] a = bucketmap.values().toArray( new AnalysisBucket[bucketmap.size()] );
        Arrays.sort(a, new Comparator<AnalysisBucket>() {
          @Override
          public int compare(AnalysisBucket arg0, AnalysisBucket arg1)
          {
            return arg0.name.compareTo(arg1.name);
          }
        } );

        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "."                                                );
          log.println( "."                                                );
          log.println( "ANALYSIS BUCKETS " );
          for ( AnalysisBucket b : a )
            log.println( "Bucket " + b.name + " dirs = " + b.dirs.size() + " files = " + b.filecount + "  storage = " + b.filesize );
          log.println( "."                                                );
          log.println( "."                                                );
          log.println( "Detailed report for big buckets."                                                );
          for ( AnalysisBucket b : a )
          {
            if ( b.filesize > 100000000 )
            {
              log.println( "Big Bucket " + b.name + "   dirs = " + b.dirs.size() + "   files = " + b.filecount + "   storage = " + b.filesize );
              for ( SubBucket subbucket : b.dirs )
                if ( subbucket.filesize > 100000000 )
                  log.println( "   Big Subbucket " + subbucket.name + "   files = " + subbucket.filecount + "   storage = " + subbucket.filesize );
            }
          }
          log.println( "End of report."                                                );
          log.println( "."                                                );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to analyse legacy files.", ex);
        }
      
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
      Path logfile = bbmonitor.logbase.resolve( "turnitinfilesanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      Path coursebase = bbmonitor.virtualserverbase.resolve( "courses/1/" );
      long totalgood = 0L;
      long totalbad = 0L;
      
      try
      {

        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to analyse turnitin files in legacy file system. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to analyse turnitin files.", ex);
          return;
        }

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
                  if ( name.startsWith( courseid+"_" ) )
                    totalgood += Files.size( uploadedfile );
                  else
                    totalbad += Files.size( uploadedfile );
                }
              }
            }
          }
        }      

        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Analysis of turnitin submissions in legacy file system."       );
          log.println( "Bytes of data that belong to the module = "        + totalgood );
          log.println( "Bytes of data that DO NOT belong to the module = " + totalbad  );
          log.println( "End of report."                                                );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to analyse turnitin files.", ex);
        }
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
      Path logfile = bbmonitor.logbase.resolve( "turnitinpruning-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      try
      {
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to prune turnitin files in legacy file system. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to prune turnitin files.", ex);
          return;
        }
        
        long start = System.currentTimeMillis();
        bbmonitor.logger.info( "Turn It In pruning process started." );
        int filesmoved = 0;

        Path coursebase = bbmonitor.virtualserverbase.resolve( "courses/1" );
        BigInteger totalgood = BigInteger.ZERO;
        BigInteger totalbad = BigInteger.ZERO;

        // Prep...
        Path tempstorepath    = bbmonitor.virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
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
                      else
                      {
                        Files.move( uploadedfile, targetfile );
                        filesmoved++;
                      }
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
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Turn It In pruning process ended after " + elapsed + " seconds. " + filesmoved + " files moved."       );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to prune turnitin files.", ex);
        }
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
      Path logfile = bbmonitor.logbase.resolve( "turnitinunpruning-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      try
      {
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to unprune turnitin files in legacy file system. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to unprune turnitin files.", ex);
          return;
        }
        
        long start = System.currentTimeMillis();
        bbmonitor.logger.info( "Turn It In Unpruning process started." );
        int filesmoved = 0;

        Path coursebase = bbmonitor.virtualserverbase.resolve( "courses/1" );
        BigInteger totalgood = BigInteger.ZERO;
        BigInteger totalbad = BigInteger.ZERO;

        // Prep...
        Path tempstorepath    = bbmonitor.virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
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
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Turn It In unpruning process ended after " + elapsed + " seconds. " + filesmoved + " files moved."       );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to unprune turnitin files.", ex);
        }
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
      Path logfile = bbmonitor.logbase.resolve( "turnitindelete-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

      try
      {
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to unprune turnitin files in legacy file system. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to delete turnitin files.", ex);
          return;
        }
        
        bbmonitor.logger.info( "Turn It In permanently deleting process started. May take many minutes. " ); 
        long start = System.currentTimeMillis();
        Path tempstorepath    = bbmonitor.virtualserverbase.resolve( "courses_TEMPORARY_COPIES" );
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
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Turn It In permanently deleting process ended after " + elapsed + " seconds. "      );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to delete turnitin files.", ex);
        }
      }
      finally
      {
        currenttask = null;
      }
    }
    
  }  

}
