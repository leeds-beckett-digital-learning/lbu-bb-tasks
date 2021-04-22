/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.platform.plugin.PlugInUtil;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.xythos.common.InternalException;
import com.xythos.common.api.NetworkAddress;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
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
import com.xythos.storageServer.properties.api.Property;
import com.xythos.storageServer.properties.api.PropertyDefinition;
import com.xythos.storageServer.properties.api.PropertyDefinitionManager;
import com.xythos.webdav.dasl.api.DaslResultSet;
import com.xythos.webdav.dasl.api.DaslStatement;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
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
  
  public static final String CUSTOM_PROPERTIES_NAMESPACE       = "my.leedsbeckett.ac.uk/mediaanalysis";
  public static final String CUSTOM_PROPERTY_ANALYSEDETAG      = "analysedetag";
  public static final String CUSTOM_PROPERTY_MEDIADATARATE     = "mediadatarate";
  public static final String CUSTOM_PROPERTY_MEDIADURATION     = "mediaduration";
  public static final String CUSTOM_PROPERTY_MIMETYPE          = "mimetype";
  public static final String CUSTOM_PROPERTY_MEDIALOG          = "medialog";

  
  
  public static final String VIDEO_SEARCH_DASL = 
 "<?xml version=\"1.0\" ?>"
+"  <d:searchrequest xmlns:d=\"DAV:\" xmlns:m=\"" + CUSTOM_PROPERTIES_NAMESPACE + "\">"
+"  <d:basicsearch>"
+"    <d:select>"
+"      <d:allprop/>"
+"    </d:select>"
+"    <d:from>"
+"      <d:scope>"
+"        <d:href>https://my-staging.leedsbeckett.ac.uk/bbcswebdav/</d:href>"
+"        <d:depth>infinity</d:depth>"
+"      </d:scope>"
+"    </d:from>"
+"    <d:where>"
+"      <d:like> "
+"        <d:prop><d:getcontenttype/></d:prop>"
//+"        <d:literal>video/mp4</d:literal>"
+"        <d:literal>video/%</d:literal>"
+"      </d:like>"
+"    </d:where>"
+"  </d:basicsearch>"
+"</d:searchrequest>";
  
  
  
  
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

    
    String testemail = req.getParameter("testemail");
    if ( testemail != null && testemail.length() > 0 )
    {
      doTestEmail( req, resp );
      return;
    }

    String analysevideofiles = req.getParameter("analysevideofiles");
    if ( analysevideofiles != null && analysevideofiles.length() > 0 )
    {
      doAnalyseVideoFiles( req, resp );
      return;
    }
    

    String sy = req.getParameter("year");
    String sm = req.getParameter("month");
    String sd = req.getParameter("date");
    String sy2 = req.getParameter("year2");
    String sm2 = req.getParameter("month2");
    String sd2 = req.getParameter("date2");
    int y, m, d, y2, m2, d2;
    try
    {
      y = Integer.parseInt( sy );
      m = Integer.parseInt( sm );
      d = Integer.parseInt( sd );
      y2 = Integer.parseInt( sy2 );
      m2 = Integer.parseInt( sm2 );
      d2 = Integer.parseInt( sd2 );
    }
    catch ( NumberFormatException nfe )
    {
      sendError( req, resp, "Non-numeric data in date fields.");
      return;
    }

    if ( y<2020 || y>2100 || y<2020 || y>2100 )
    {
      sendError( req, resp, "Invalid date.");
      return;
    }
    
    String analyseautoarchives = req.getParameter("analyseautoarchives");
    if ( analyseautoarchives != null && analyseautoarchives.length() > 0 )
    {
      doAnalyseAutoArchives( req, resp, y, m, d, y2, m2, d2 );
      return;
    }
    
    String analysedeletedautoarchives = req.getParameter("analysedeletedautoarchives");
    if ( analysedeletedautoarchives != null && analysedeletedautoarchives.length() > 0 )
    {
      doAnalyseDeletedAutoArchives( req, resp, y, m, d, y2, m2, d2 );
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

  protected void doTestEmail(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Sending Test Email</h1>" );
      
      try
      {
        EMailSender.sendTestMessage();
        out.println( "<p>No errors reported.</p>" );
      }
      catch ( Exception ex )
      {
        ex.printStackTrace( new PrintWriter( out ) );
      }
      
      out.println( "</body></html>" );      
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
  
  
  protected void doAnalyseAutoArchives(HttpServletRequest req, HttpServletResponse resp, int y, int m, int d, int y2, int m2, int d2 )
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
        currenttask = new AnalyseAutoArchiveThread( vs, y, m, d, y2, m2, d2 );
        currenttask.start();
        out.println( "<h2>Autoarchive analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }

  protected void doAnalyseVideoFiles(HttpServletRequest req, HttpServletResponse resp )
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
        String action = req.getParameter( "action" );
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        currenttask = new AnalyseVideoThread( vs, action );
        currenttask.start();
        out.println( "<h2>Video file analysis started</h2>" );
        out.println( "<p>Results will enter the log.</p>" );
      }
      out.println( "</body></html>" );      
    }      
  }

  protected void doAnalyseDeletedAutoArchives(HttpServletRequest req, HttpServletResponse resp, int y, int m, int d, int y2, int m2, int d2)
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
        currenttask = new AnalyseDeletedAutoArchiveThread( vs, y, m, d, y2, m2, d2 );
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
      zipfile = new ZipFile( 
              channel, 
              "unknown archive", 
              "UTF8", 
              true,      // useUnicodeExtraFields
              true       // ignoreLocalFileHeader
      );
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
      zipfile = new ZipFile( 
              channel, 
              "unknown archive", 
              "UTF8", 
              true,      // useUnicodeExtraFields
              true       // ignoreLocalFileHeader
      );
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
    
  protected static void findBinaryObjects(final JDBCConnection p_conn, List<BlobSearchResult> list, int y, int m, int d, int y2, int m2, int d2 ) throws SQLException, InternalException
  {
    PreparedStatement l_stmt = null;
    ResultSet l_rset = null;
    BinaryObject l_retValue = null;
    BlobSearchResult bsr;
    String datea = "'" + y + "-" + m + "-" + d + " 00:00:00.0'";
    String dateb = "'" + y2 + "-" + m2 + "-" + d2 + " 23:59:59.9'";
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
    
    int y, m, d, y2, m2, d2;
    
    public AnalyseDeletedAutoArchiveThread( VirtualServer vs, int y, int m, int d, int y2, int m2, int d2 )
    {
      this.vs = vs;
      this.y = y;
      this.m = m;
      this.d = d;
      this.y2 = y2;
      this.m2 = m2;
      this.d2 = d2;
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
                
                findBinaryObjects( l_dbcon, list, y, m, d, y2, m2, d2 );
                
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
//                medialog.append( " " + Integer.toHexString( (0xff & x) | 0x100 ).substring(1) );
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
    int datecode, datecode2;
    Calendar calends;
    
    public AnalyseAutoArchiveThread( VirtualServer vs, int y, int m, int d, int y2, int m2, int d2 )
    {
      this.vs = vs;
      datecode  =  y*10000 +  m*100 + d;
      datecode2 = y2*10000 + m2*100 + d2;
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
        
        bbmonitor.logger.info( "Analyse autoarchives process started. May take many minutes. " + datecode + " to " + datecode2 ); 
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
                int fdatecode = dt.getYear() * 10000 + dt.getMonthValue()*100 + dt.getDayOfMonth();
                bbmonitor.logger.info( "Zip file created date = " + fdatecode ); 
                if ( fdatecode >= datecode && fdatecode <= datecode2 )
                {
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
//                medialog.append( " " + Integer.toHexString( (0xff & x) | 0x100 ).substring(1) );
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


  class AnalyseVideoThread extends Thread
  {
    VirtualServer vs;
    Calendar calends;
    int analyses=0; 
    PropertyDefinition propdefetag, propdefrate, propdefduration, propdefmimetype, propdefmedialog;
    String query = VIDEO_SEARCH_DASL;
    ArrayList<String> list = new ArrayList<>();
    String action;
    
    public AnalyseVideoThread( VirtualServer vs, String action )
    {
      this.setPriority(MIN_PRIORITY);
      this.vs = vs;
      this.action = action;
      bbmonitor.logger.info( query );
      calends = Calendar.getInstance( TimeZone.getTimeZone( "GMT" ) );
    }
      
    @Override
    public void run()
    {
      Path logfile = bbmonitor.logbase.resolve( "videoanalysis-" + dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

      try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
      {
        bbmonitor.logger.info( "Analyse video files process started. May take many minutes. " ); 
        prepare( log );
        long start = System.currentTimeMillis();

        if ( !findVideoFiles(log) )
          bbmonitor.logger.info( "Unable to build list of video files. " ); 
        else
        {
          bbmonitor.logger.info( "Complete ist of video files. Size = " + list.size() );
          for ( String id : list )
          {
            if ( "analyse".equals( action ) )
              processOne( log, id );
            if ( "clear".equals( action ) )
              clearOne( log, id );
            if ( "list".equals( action ) )
              listOne( log, id );
            log.flush();
            if ( analyses > 100000 )
              break;
          }
        }
        
        long end = System.currentTimeMillis();
        float elapsed = 0.001f * (float)(end-start);
        bbmonitor.logger.info( "Analyse video file process ended after " + elapsed + " seconds. " );
      }
      catch ( Exception ex )
      {
        bbmonitor.logger.error( "Error attempting to analyse video files.", ex);
      }
      finally
      {
        currenttask = null;
      }
    }
    
    void prepare( PrintWriter log ) throws XythosException
    {
      Context context = null;
      try
      {
        context = AdminUtil.getContextForAdmin( "VideoAnalysis" );

        propdefetag = PropertyDefinitionManager.findPropertyDefinition( CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_ANALYSEDETAG, context );
        if ( propdefetag == null )
          propdefetag = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE, 
                CUSTOM_PROPERTY_ANALYSEDETAG, 
                PropertyDefinition.DATATYPE_SHORT_STRING, 
                false,  // not versioned 
                true,   // readable
                true,   // writable
                false,  // not caseinsensitive
                false,  // not protected
                false,  // not full text indexed
                "The etag value when the media metadata was last analysed."     );
        propdefmimetype = PropertyDefinitionManager.findPropertyDefinition( CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MIMETYPE, context );
        if ( propdefmimetype == null )
          propdefmimetype = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE, 
                CUSTOM_PROPERTY_MIMETYPE, 
                PropertyDefinition.DATATYPE_SHORT_STRING, 
                false,  // not versioned 
                true,   // readable
                true,   // writable
                false,  // not caseinsensitive
                false,  // not protected
                false,  // not full text indexed
                "The mimetype determined from metadata in the file."     );
        propdefmimetype = PropertyDefinitionManager.findPropertyDefinition( CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MIMETYPE, context );
        if ( propdefmedialog == null )
          propdefmedialog = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE, 
                CUSTOM_PROPERTY_MEDIALOG, 
                PropertyDefinition.DATATYPE_STRING, 
                false,  // not versioned 
                true,   // readable
                true,   // writable
                false,  // not caseinsensitive
                false,  // not protected
                false,  // not full text indexed
                "Logging text from the media analysis process."     );
        propdefrate = PropertyDefinitionManager.findPropertyDefinition( CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADATARATE, context );
        if ( propdefrate == null )
          propdefrate = PropertyDefinitionManager.createIndexedLongPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADATARATE, true, true, false, false, "Average data rate of media in bytes per second.", context);
        propdefduration = PropertyDefinitionManager.findPropertyDefinition( CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADURATION, context );
        if ( propdefduration == null )
          propdefduration = PropertyDefinitionManager.createIndexedLongPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADURATION, true, true, false, false, "Media duration in seconds.", context);
      }
      finally
      {
        try
        {
          if ( context != null )
            context.commitContext();
        } 
        catch (XythosException ex)
        {
          bbmonitor.logger.error( "Error occured trying to commit xythos context.", ex );
        }
      }
    }
    
    
    boolean findVideoFiles( PrintWriter log )
    {
      Context context = null;
      try
      {
        context = AdminUtil.getContextForAdminUI( "VideoAnalysis" + System.currentTimeMillis() );
        DaslStatement statement = new DaslStatement( query, context );
        DaslResultSet resultset = statement.executeDaslQuery();
        
        while ( resultset.nextEntry() )
        {
          FileSystemEntry fse = resultset.getCurrentEntry();
          if ( !(fse instanceof com.xythos.fileSystem.File) )
            continue;
          com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
          //bbmonitor.logger.info( "Found video file " + fse.getName() );
          list.add( fse.getEntryID() );
        }
      }
      catch ( Throwable th )
      {
        bbmonitor.logger.error( "Error occured while finding list of video files.", th );
        return false; // error so stop processing
      }
      finally
      {
        try
        {
          if ( context != null )
            context.commitContext();
        } 
        catch (XythosException ex)
        {
        }
      }
      return true;
    }
    
    
    void clearOne( PrintWriter log, String id )
    {
      Context context = null;
      try
      {
        context = AdminUtil.getContextForAdminUI( "VideoAnalysis" + System.currentTimeMillis() );
        FileSystemEntry fse = FileSystem.findEntryFromEntryID( id, false, context );
        if( fse == null )
          return;
        if ( !(fse instanceof com.xythos.fileSystem.File) )
          return;
        com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
        
        Property aetag = f.getProperty(propdefetag, true, context);
        if ( aetag != null )
          f.deleteProperty( aetag, false, context );                
      }
      catch ( Throwable th )
      {
        bbmonitor.logger.error( "Error occured while clearing etag property on video files.", th );
        return; // error so stop processing
      }
      finally
      {
        try
        {
          if ( context != null )
            context.commitContext();
        } 
        catch (XythosException ex)
        {
        }
      }
      return;
    }
    
    
    void processOne( PrintWriter log, String id )
    {
      Context context = null;
      try
      {
        context = AdminUtil.getContextForAdminUI( "VideoAnalysis" + System.currentTimeMillis() );
        FileSystemEntry fse = FileSystem.findEntryFromEntryID( id, false, context );
        if( fse == null )
          return;
        if ( !(fse instanceof com.xythos.fileSystem.File) )
          return;
        com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
        Revision r = f.getLatestRevision();
        bbmonitor.logger.info( "Found video file " + fse.getName() );       

        VideoSearchResult vsr = new VideoSearchResult();
        vsr.etag = r.getETagValue();
        Property aetag = f.getProperty(propdefetag, true, context);
        if ( aetag != null )
        {
          vsr.analysedetag = aetag.getValue();
          bbmonitor.logger.info( "Comparing " + vsr.etag + " with " + vsr.analysedetag );
          if ( vsr.analysedetag.equals( vsr.etag ) )
          {
            bbmonitor.logger.info( "Analysis is up to date." );
            return;
          }
        }
        
        vsr.analysedetag = vsr.etag;
        vsr.id   = r.getBlobID();
        vsr.path = f.getName();
        vsr.size = r.getSize();
        vsr.recordedmimetype = f.getFileMimeType();
        analyseVideoFile( vsr, r );

        if ( vsr.detectedmimetype != null )
        {
          Property p = f.getProperty(propdefmimetype, true, context);
          if ( p != null )
            f.deleteProperty( p, false, context );
          f.addShortStringProperty(propdefmimetype, vsr.detectedmimetype, false, context);
        }
        
        if ( vsr.duration >= 0 )
        {
          Property p = f.getProperty(propdefduration, true, context);
          if ( p != null )
            f.deleteProperty( p, false, context );
          f.addLongProperty(propdefduration, vsr.duration, false, context);
        }
        
        if ( vsr.duration > 0 && vsr.size > 0 )
        {
          vsr.datarate = vsr.size / vsr.duration;
          Property p = f.getProperty(propdefrate, true, context);
          if ( p != null )
            f.deleteProperty( p, false, context );
          f.addLongProperty(propdefrate, vsr.datarate, false, context);
        }

        if ( vsr.medialog != null )
        {
          Property p = f.getProperty(propdefmedialog, true, context);
          if ( p != null )
            f.deleteProperty( p, false, context );
          f.addStringProperty(propdefmedialog, vsr.medialog, false, context);
        }
        
        if ( true || vsr.analysed )
        {
          log.append( vsr.toString() );
          bbmonitor.logger.info(vsr.medialog );
        }

        // finish off by marking the file as analysed
        if ( aetag != null )
          f.deleteProperty(aetag, false, context);
        f.addShortStringProperty(propdefetag, vsr.etag, false, context);
        analyses++;
        
      }
      catch ( Throwable th )
      {
        bbmonitor.logger.error( "Error occured while running analysis of video files.", th );
        return; // error so stop processing
      }
      finally
      {
        try
        {
          if ( context != null )
            context.commitContext();
        } 
        catch (XythosException ex)
        {
          bbmonitor.logger.error( "Error occured while running analysis of video files.", ex );
          return;
        }
      }
      return; // there might be more data to process...
    }
    
    
    void listOne( PrintWriter log, String id )
    {
      Context context = null;
      try
      {
        context = AdminUtil.getContextForAdminUI( "VideoAnalysis" + System.currentTimeMillis() );
        FileSystemEntry fse = FileSystem.findEntryFromEntryID( id, false, context );
        if( fse == null )
          return;
        if ( !(fse instanceof com.xythos.fileSystem.File) )
          return;
        com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
        Revision r = f.getLatestRevision();
                                
        VideoSearchResult vsr = new VideoSearchResult();
        vsr.etag = r.getETagValue();
        Property aetag = f.getProperty(propdefetag, true, context);
        if ( aetag != null )
          vsr.analysedetag = aetag.getValue();
        vsr.id = r.getBlobID();
        vsr.path = f.getName();
        vsr.size = r.getSize();
        vsr.recordedmimetype = f.getFileMimeType();

        Property p = f.getProperty(propdefduration, true, context);
        if ( p != null )
        {
          try { vsr.duration = Integer.valueOf( p.getValue() ); }
          catch ( NumberFormatException nfe ) { vsr.duration = -1; }
        }
        p = f.getProperty(propdefrate, true, context);
        if ( p != null )
        {
          try { vsr.datarate = Integer.valueOf( p.getValue() ); }
          catch ( NumberFormatException nfe ) { vsr.datarate = -1; }
        }
        
        p = f.getProperty(propdefmimetype, true, context);
        if ( p != null )
          vsr.detectedmimetype = p.getValue();

//        p = f.getProperty(propdefmedialog, true, context);
//        if ( p != null )
//          bbmonitor.logger.info( p.getValue() );

        log.append( vsr.toString() );

        analyses++;        
      }
      catch ( Throwable th )
      {
        bbmonitor.logger.error( "Error occured while running analysis of video files.", th );
        return; // error so stop processing
      }
      finally
      {
        try
        {
          if ( context != null )
            context.commitContext();
        } 
        catch (XythosException ex)
        {
          bbmonitor.logger.error( "Error occured while running analysis of video files.", ex );
          return;
        }
      }
      return; // there might be more data to process...
    }
    
    void analyseVideoFile( VideoSearchResult vsr, Revision r )
    {
      bbmonitor.logger.info( "Heap free memory = " + Runtime.getRuntime().freeMemory() );
      
      try ( XythosAdapterChannel channel = new XythosAdapterChannel( r );
            InputStream in = Channels.newInputStream( channel );           )
      {
        StringBuilder message = new StringBuilder();

        Metadata metadata = ImageMetadataReader.readMetadata( in );
        if ( metadata == null )
        {
          vsr.medialog = "No metadata found for file.";
          return;
        }
        
        message.append( "File metadata:\n" );
        FileTypeDirectory ftdirectory = metadata.getFirstDirectoryOfType( FileTypeDirectory.class );
        String propermime = ftdirectory.getString( FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE );
        vsr.detectedmimetype = propermime;
        
        if ( "video/quicktime".equals( vsr.detectedmimetype ) )
        {
          QuickTimeDirectory qt4d = metadata.getFirstDirectoryOfType( QuickTimeDirectory.class );
          if ( qt4d == null )
            bbmonitor.logger.error( "File has no quicktime metadata." );
          else
          {
            try
            {
              vsr.duration =qt4d.getInt( QuickTimeDirectory.TAG_DURATION_SECONDS );
            }
            catch (MetadataException ex)
            {
              bbmonitor.logger.error( "Error attempting to read video metadata.", ex);
            }
          }
        }
        else if ( "video/mp4".equals( vsr.detectedmimetype ) )
        {
          Mp4Directory mp4d = metadata.getFirstDirectoryOfType( Mp4Directory.class );
          if ( mp4d == null )
            bbmonitor.logger.error( "File has no mp4 metadata." );
          else
          {
            try
            {
              vsr.duration = mp4d.getInt( Mp4Directory.TAG_DURATION_SECONDS );
            }
            catch (MetadataException ex)
            {
              bbmonitor.logger.error( "Error attempting to read video metadata.", ex);
            }
          }
        }
        else if ( "video/vnd.avi".equals( vsr.detectedmimetype ) )
        {
          AviDirectory avi4d = metadata.getFirstDirectoryOfType( AviDirectory.class );
          if ( avi4d == null )
            bbmonitor.logger.error( "File has no avi metadata." );
          else
          {
            try
            {
              vsr.duration = avi4d.getInt( AviDirectory.TAG_DURATION )/1000000;
            }
            catch (MetadataException ex)
            {
              bbmonitor.logger.error( "Error attempting to read video metadata.", ex);
            }
          }
        }
        else
        {
          message.append( "Unrecognised mime type.\n" );
        }

        for (com.drew.metadata.Directory directory : metadata.getDirectories())
        {
          for (com.drew.metadata.Tag tag : directory.getTags())
          {
            message.append( tag );
            message.append( "\n" );
          }
        }
        vsr.medialog = message.toString();
        bbmonitor.logger.info(vsr.medialog );
      }
      catch ( Exception ex )
      {
        bbmonitor.logger.error( "Problem reading metadata in media file.", ex );
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
