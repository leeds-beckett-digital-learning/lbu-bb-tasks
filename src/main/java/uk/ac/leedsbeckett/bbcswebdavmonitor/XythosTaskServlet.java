/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import com.xythos.common.BinaryObjectStorage;
import com.xythos.common.InternalException;
import com.xythos.common.api.NetworkAddress;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.common.dbConnect.DBBinaryObjectStorage;
import com.xythos.common.dbConnect.JDBCConnection;
import com.xythos.common.dbConnect.JDBCConnectionPool;
import com.xythos.common.dbConnect.JDBCResultSetWrapper;
import com.xythos.fileSystem.BinaryObject;
import com.xythos.fileSystem.Directory;
import com.xythos.fileSystem.Revision;
import com.xythos.security.ContextImpl;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.ServerGroupImpl;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.admin.api.ServerGroup;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
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
    
    String listdeletedfiles = req.getParameter("listdeletedfiles");
    if ( listdeletedfiles != null && listdeletedfiles.length() > 0 )
    {
      doListDeletedFiles( req, resp );
      return;
    }


    String sy = req.getParameter("year");
    String sm = req.getParameter("month");
    String sd = req.getParameter("date");
    int y, m, d;
    try
    {
      y = Integer.parseInt( sy );
      m = Integer.parseInt( sm );
      d = Integer.parseInt( sd );
    }
    catch ( NumberFormatException nfe )
    {
      sendError( req, resp, "Non-numeric data in date fields.");
      return;
    }

    if ( y<2020 || y>2100 )
    {
      sendError( req, resp, "Invalid date.");
      return;
    }
    Date date = new Date();
    
    String analyseautoarchives = req.getParameter("analyseautoarchives");
    if ( analyseautoarchives != null && analyseautoarchives.length() > 0 )
    {
      doAnalyseAutoArchives( req, resp, y, m, d );
      return;
    }
    
    String analysedeletedautoarchives = req.getParameter("analysedeletedautoarchives");
    if ( analysedeletedautoarchives != null && analysedeletedautoarchives.length() > 0 )
    {
      doAnalyseDeletedAutoArchives( req, resp, y, m, d );
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

  protected void doListDeletedFiles(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Xythos Servlet</h1>" );
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        currenttask = new ListDeletedFilesThread( vs );
        currenttask.start();
        out.println( "<h2>Deleted files listing started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }
  
  
  protected void doAnalyseAutoArchives(HttpServletRequest req, HttpServletResponse resp, int y, int m, int d )
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
      out.println( "<h1>Xythos Servlet</h1>" );
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        currenttask = new AnalyseAutoArchiveThread( vs, y, m, d );
        currenttask.start();
        out.println( "<h2>Autoarchive analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }

  protected void doAnalyseDeletedAutoArchives(HttpServletRequest req, HttpServletResponse resp, int y, int m, int d)
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
      out.println( "<h1>Xythos Servlet</h1>" );
      
      if ( currenttask != null )
      {
        out.println( "<p>A Task is already in progress.</p>" );
      }
      else
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        currenttask = new AnalyseDeletedAutoArchiveThread( vs, y, m, d );
        currenttask.start();
        out.println( "<h2>Deleted autoarchive analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }

  private void analyseZip( BinaryObject zip, BlobSearchResult bsr ) throws XythosException, IOException
  {
    bbmonitor.logger.info( "Start of zip analysis of blob. " + zip.getBlobID() );
    XythosAdapterChannel channel = null;
    ZipFile zipfile = null;
    try
    {
      bsr.analysed = true;
      bsr.binsize = zip.getSize();
      channel = new XythosAdapterChannel( zip );
      //channel.setLogger( bbmonitor.logger );
      zipfile = new ZipFile( channel );
      analyseZip( zipfile, bsr );
      bsr.iszip = true;
    }
    catch ( ZipException ze )
    {
      bbmonitor.logger.info( "Not a zip file." );
      bsr.iszip = false;
    }
    finally
    {
      if ( zipfile != null )
        zipfile.close();
      if ( channel != null )
        channel.close();
    }
  }
  
  private void analyseZip( com.xythos.fileSystem.File zip, BlobSearchResult bsr ) throws XythosException, IOException
  {
    bbmonitor.logger.info( "Start of zip analysis of file revision. " + zip.getName() + "(" + zip.getEntrySize() + "bytes)" );
    XythosAdapterChannel channel = null;
    ZipFile zipfile = null;
    try
    {
      Revision r = zip.getLatestRevision();
      bsr.analysed = true;
      bsr.created = zip.getCreationTimestamp();
      bsr.id = r.getBlobID();
      bsr.size = r.getSize();
      bsr.iszip = true;
      
      channel = new XythosAdapterChannel( r );
      //channel.setLogger( bbmonitor.logger );
      zipfile = new ZipFile( channel );
      analyseZip( zipfile, bsr );
    }    
    finally
    {
      if ( zipfile != null )
        zipfile.close();
      if ( channel != null )
        channel.close();
    }
  }
  
  private void analyseZip( ZipFile zipfile, BlobSearchResult bsr ) throws XythosException, IOException
  {
    Enumeration<ZipArchiveEntry> e = zipfile.getEntries();
    while ( e.hasMoreElements() )
    {
      ZipArchiveEntry entry = e.nextElement();
      String name = entry.getName();
      //bbmonitor.logger.info( "Name = " + name );
      if ( name.startsWith( "ppg/BB_Direct/Uploads/" ))
        bsr.turnitinusage += entry.getSize();
      if ( name.startsWith( "csfiles/" ))
        bsr.csfilesusage += entry.getSize();
    }
    bbmonitor.logger.info( "End of zip analysis." );
  }

  protected String[] getFileSystems() throws XythosException {
      Context l_adminContext = null;
      try {
          l_adminContext = AdminUtil.getContextForAdmin("ProcessDocstoreThread.getFileSystems");
          return ((ServerGroupImpl)ServerGroup.getCurrentServerGroup()).getManagedDocumentStoreNames(l_adminContext);
      }
      finally {
          if (l_adminContext != null) {
              l_adminContext.rollbackContext();
          }
      }
  }
    
  protected static void findBinaryObjects(final JDBCConnection p_conn, List<BlobSearchResult> list, int y, int m, int d ) throws SQLException, InternalException
  {
    PreparedStatement l_stmt = null;
    ResultSet l_rset = null;
    BinaryObject l_retValue = null;
    BlobSearchResult bsr;
    String datea = "'" + y + "-" + m + "-" + d + " 00:00:00.0'";
    String dateb = "'" + y + "-" + m + "-" + d + " 23:59:59.9'";
    final String l_sql = "SELECT BLOB_ID, STORAGE_STATE FROM XYF_BLOBS WHERE REF_COUNT = 0 AND STORAGE_DATE >= " + datea + " AND STORAGE_DATE <= " + dateb;
    try {
        l_stmt = p_conn.prepareStatement(l_sql);
        l_rset = l_stmt.executeQuery();
        final JDBCResultSetWrapper l_rsWrap = p_conn.getJDBCResultSetWrapper(l_stmt, l_rset);
        for ( int i=0; l_rset.next(); i++ )
        {
          bsr = new BlobSearchResult();
          bsr.id = l_rsWrap.getLong(1);
          bsr.storagestate = l_rsWrap.getInt(2);
          list.add(bsr);
        }
    }
    finally {
        if (l_rset != null) {
            try {
                l_rset.close();
            }
            catch (SQLException ex) {}
        }
        if (l_stmt != null) {
            try {
                l_stmt.close();
            }
            catch (SQLException ex2) {}
        }
    }
  }

  protected static void listBinaryObjects(final JDBCConnection p_conn, PrintWriter writer ) throws SQLException, InternalException
  {
    PreparedStatement l_stmt = null;
    ResultSet l_rset = null;
    final String l_sql = "SELECT BLOB_ID, REF_COUNT, BLOB_SIZE, BLOB_STATE, STORAGE_STATE, STORAGE_DATE, DIGEST, STORAGE_LOCATION, STORAGE_FILENAME, TEMPORARY_STORAGE_LOCATION, TEMPORARY_STORAGE_FILENAME, DATA, RECOVER_DATE FROM XYF_BLOBS "
    +"WHERE REF_COUNT = 0";
    //+"WHERE ((STORAGE_STATE = 60 AND BLOB_STATE = 'D') OR (STORAGE_STATE != 70 AND BLOB_STATE = 'C'))";
    try {
        l_stmt = p_conn.prepareStatement(l_sql);
        l_rset = l_stmt.executeQuery();
        for ( int i=0; l_rset.next(); i++ )
        {
          writer.print( l_rset.getLong(1) );
          writer.print( "," );
          writer.print( l_rset.getInt(2) );
          writer.print( "," );
          writer.print( l_rset.getLong(3) );
          writer.print( ",\"" );
          writer.print( l_rset.getString(4) );
          writer.print( "\"," );
          writer.print( l_rset.getInt(5) );
          writer.print( "," );
          writer.print( l_rset.getTimestamp(6) );
          writer.print( ",\"" );
          writer.print( l_rset.getString(7) );
          writer.print( "\"," );
          writer.print( l_rset.getInt(8) );
          writer.print( ",\"" );
          writer.print( l_rset.getString(9) );
          writer.print( "\"," );
          writer.print( l_rset.getInt(10) );
          writer.print( ",\"" );
          writer.print( l_rset.getString(11) );
          writer.print( "\"," );
          writer.print( l_rset.getLong(12) );
          writer.print( "," );
          writer.println( l_rset.getTimestamp(13) );
        }
    }
    finally {
        if (l_rset != null) {
            try {
                l_rset.close();
            }
            catch (SQLException ex) {}
        }
        if (l_stmt != null) {
            try {
                l_stmt.close();
            }
            catch (SQLException ex2) {}
        }
    }
  }
  
  void getBinaryObject(final JDBCConnection p_conn, BlobSearchResult bsr ) throws SQLException, InternalException, IOException, XythosException
  {
    bbmonitor.logger.info( "Loading " + bsr.id );
    BinaryObject blob = null;
    try
    {
      blob = BinaryObject.getExpectedBinaryObject(p_conn, bsr.id, bsr.storagestate );
      bsr.refcount = blob.getRefCount();
      bsr.size = blob.getSize();
      bsr.storagefilename = blob.getStorageName();
      bsr.tempstoragefilename = blob.getTemporaryStorageName();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      if ( bsr.size >= 4 )
        blob.getBytes(baos, 0, 4, false, false, false, false, null, null, null, true);
      StringBuilder sb = new StringBuilder();
      bsr.signature = baos.toByteArray();
      bsr.iszip = Signatures.isZip( bsr.signature );
      bsr.created = blob.getStorageDate();
      if ( bsr.iszip )
        analyseZip( blob, bsr );
    }
    finally
    {
//      if ( blob != null )
//        blob.closeAfterRead(p_context);
    }
  }

  
  class AnalyseDeletedAutoArchiveThread extends Thread
  {
    VirtualServer vs;
    int y, m, d;
    
    public AnalyseDeletedAutoArchiveThread( VirtualServer vs, int y, int m, int d )
    {
      this.vs = vs;
      this.y = y;
      this.m = m;
      this.d = d;
    }
      
    @Override
    public void run()
    {
      ArrayList<BlobSearchResult> list = new ArrayList<>();
      Path logfile = bbmonitor.logbase.resolve( "deletedautoarchiveanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      long tiirunningtotal = 0L;
      long csrunningtotal = 0L;

      try
      {
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( "Starting to analyse deleted autoarchives. This may take many minutes." );
        } catch (IOException ex)
        {
          bbmonitor.logger.error( "Error attempting to analyse deleted autoarchives.", ex);
          return;
        }
        
        bbmonitor.logger.info( "Analyse deleted autoarchives process started. May take many minutes. " ); 
        long start = System.currentTimeMillis();

        Context context = null;
        FileSystemEntry entry = null;
        StringBuilder message = new StringBuilder();
        try
        {
          String[] l_fileSystems = getFileSystems();
          for (int i = 0; i < l_fileSystems.length; ++i)
          {
            // modelled after com.xythos.util.ProcessDocstoreThread.performAction()
            final JDBCConnectionPool l_pool = JDBCConnectionPool.getJDBCConnectionPool(l_fileSystems[i]);
            if (!l_pool.getType().equals("DOCUMENT_STORE")) {
                continue;
            }
            ContextImpl l_adminContext = (ContextImpl)AdminUtil.getContextForAdmin("AnalyseAutoArchiveThread:285");
            l_adminContext.setOperationTypeIfNotSet(14);
            JDBCConnection l_dbcon = null;
            try {
                l_dbcon = l_adminContext.getDBConnection(l_pool.getPoolID());
                l_dbcon.setNeedToCommit(true);
                
                findBinaryObjects( l_dbcon, list, y, m, d );
                
                for ( int j=0; j<list.size(); j++ )
                {
                  bbmonitor.logger.info( "Working on entry " + j + " of " + list.size() );
                  BlobSearchResult bsr = list.get(j);
                  getBinaryObject( l_dbcon, bsr );                  
                }
                
                l_adminContext.commitContext();
                l_adminContext = null;
                l_dbcon = null;
            }
            finally {
                if (l_adminContext != null) {
                    l_adminContext.rollbackContext();
                    l_adminContext = null;
                }
            }          
          }
        }
        catch ( Throwable th )
        {
          bbmonitor.logger.error( "Error occured while running analysis of deleted autoarchives.", th);
        }
        
        int n=0;
        for ( BlobSearchResult i : list )
        {
          if ( i.analysed )
          {
            n++;
            message.append( "blob," + i.id + "," + i.refcount + "," + i.size + "," + i.storagefilename + "," + i.tempstoragefilename + "," + (i.iszip?"zip":"other") + "," + i.turnitinusage + "," + i.csfilesusage + "," );
            if ( i.created == null )
              message.append( "unknowndate" );
            else
            {
              LocalDateTime dt = i.created.toLocalDateTime();
              message.append( dt.getYear() );
              message.append( "/" );
              message.append( dt.getMonthValue() );
              message.append( "/" );
              message.append( dt.getDayOfMonth() );
            }
//            if ( i.signature != null )
//              for ( byte x : i.signature )
//                message.append( " " + Integer.toHexString( (0xff & x) | 0x100 ).substring(1) );
            tiirunningtotal += i.turnitinusage;
            csrunningtotal  += i.csfilesusage;
            message.append( "\n" );
          }
        }
        message.append( "Blob count = " + list.size() + " analysed zips = " + n + "\n" );
        
        message.append( "Found " + tiirunningtotal + " bytes of Turnitin uploads in the deleted autoarchives." );
        message.append( "Found " + csrunningtotal + " bytes of 'legacy' content uploads in the deleted autoarchives." );
        
        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          log.println( message );
          log.println( "Analyse deleted autoarchives process ended after " + elapsed + " seconds. "      );
        }
        catch ( Exception ex )
        {
          bbmonitor.logger.error( "Error attempting to analyse deleted autoarchives.", ex);
        }
      }
      finally
      {
        currenttask = null;
      }
      bbmonitor.logger.error( "Done attempting to analyse deleted autoarchives." );
    }
    
  }  


  class AnalyseAutoArchiveThread extends Thread
  {
    VirtualServer vs;
    int y, m, d;
    Calendar calends;
    
    public AnalyseAutoArchiveThread( VirtualServer vs, int y, int m, int d )
    {
      this.vs = vs;
      this.y = y;
      this.m = m;
      this.d = d;
      calends = Calendar.getInstance( TimeZone.getTimeZone( "GMT" ) );
    }
      
    @Override
    public void run()
    {
      ArrayList<BlobSearchResult> list = new ArrayList<>();
      Path logfile = bbmonitor.logbase.resolve( "autoarchiveanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
      long tiirunningtotal = 0L;
      long csrunningtotal = 0L;

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
        
        bbmonitor.logger.info( "Analyse autoarchives process started. May take many minutes. " + y + "/" + m + "/" + d ); 
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
              for ( int i=0; i < entries.length; i++ )
              {
                FileSystemEntry f = entries[i];
                if ( !(f instanceof com.xythos.fileSystem.File) )
                  continue;
                if ( !"application/zip".equals( f.getFileContentType() ) )
                  continue;
                LocalDateTime dt = f.getCreationTimestamp().toLocalDateTime();
                bbmonitor.logger.info( "Zip file created date = " + dt.getYear() + " " + dt.getMonthValue() + " " + dt.getDayOfMonth() ); 
                if ( dt.getYear()       != y || 
                     dt.getMonthValue() != m || 
                     dt.getDayOfMonth() != d    )
                  continue;
                bbmonitor.logger.info( "Working on entry " + i + " of " + entries.length );
                BlobSearchResult bsr = new BlobSearchResult();
                list.add(bsr);
                analyseZip( (com.xythos.fileSystem.File)f, bsr );
                tiirunningtotal += bsr.turnitinusage;
                csrunningtotal += bsr.csfilesusage;
              }
            }
          }
        }
        catch ( Throwable th )
        {
          bbmonitor.logger.error( "Error occured while running analysis of autoarchives.", th);
        }
        
        int n=0;
        for ( BlobSearchResult i : list )
        {
          if ( i.analysed )
          {
            n++;
            message.append( "blob," + i.id + "," + i.refcount + "," + i.size + "," + i.storagefilename + "," + i.tempstoragefilename + "," + (i.iszip?"zip":"other") + "," + i.turnitinusage + "," + i.csfilesusage + "," );
            if ( i.created == null )
              message.append( "unknowndate" );
            else
            {
              LocalDateTime dt = i.created.toLocalDateTime();
              message.append( dt.getYear() );
              message.append( "/" );
              message.append( dt.getMonthValue() );
              message.append( "/" );
              message.append( dt.getDayOfMonth() );
            }
//            if ( i.signature != null )
//              for ( byte x : i.signature )
//                message.append( " " + Integer.toHexString( (0xff & x) | 0x100 ).substring(1) );
            message.append( "\n" );
          }
        }
        message.append( "Blob count = " + list.size() + " analysed zips = " + n + "\n" );
        message.append( "Found " + tiirunningtotal + " bytes of Turnitin uploads in the autoarchives." );
        message.append( "Found " + csrunningtotal + " bytes of 'legacy' content uploads in the autoarchives." );
        
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


  class ListDeletedFilesThread extends Thread
  {
    VirtualServer vs;
    
    public ListDeletedFilesThread( VirtualServer vs )
    {
      this.vs = vs;
    }
      
    @Override
    public void run()
    {
      ArrayList<BlobSearchResult> list = new ArrayList<>();
      Path logfile = bbmonitor.logbase.resolve( "deletedfiles-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".csv" );

      try
      {
        bbmonitor.logger.info( "List started. May take many minutes. " ); 
        long start = System.currentTimeMillis();

        Context context = null;
        FileSystemEntry entry = null;
        StringBuilder message = new StringBuilder();
        try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
        {
          String[] l_fileSystems = getFileSystems();
          for (int i = 0; i < l_fileSystems.length; ++i)
          {
            // modelled after com.xythos.util.ProcessDocstoreThread.performAction()
            final JDBCConnectionPool l_pool = JDBCConnectionPool.getJDBCConnectionPool(l_fileSystems[i]);
            if (!l_pool.getType().equals("DOCUMENT_STORE")) {
                continue;
            }
            ContextImpl l_adminContext = (ContextImpl)AdminUtil.getContextForAdmin("AnalyseAutoArchiveThread:285");
            l_adminContext.setOperationTypeIfNotSet(14);
            JDBCConnection l_dbcon = null;
            try {
                l_dbcon = l_adminContext.getDBConnection(l_pool.getPoolID());
                l_dbcon.setNeedToCommit(true);
                
                listBinaryObjects( l_dbcon, log );
                
                l_adminContext.commitContext();
                l_adminContext = null;
                l_dbcon = null;
            }
            finally {
                if (l_adminContext != null) {
                    l_adminContext.rollbackContext();
                    l_adminContext = null;
                }
            }          
          }
        }
        catch ( Throwable th )
        {
          bbmonitor.logger.error( "Error occured while running analysis of deleted autoarchives.", th);
        }
        
        
        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        bbmonitor.logger.info( "Analyse deleted autoarchives process ended after " + elapsed + " seconds. " );
      }
      finally
      {
        currenttask = null;
      }
      bbmonitor.logger.error( "Done attempting to analyse deleted autoarchives." );
    }
    
  }  
  
}
