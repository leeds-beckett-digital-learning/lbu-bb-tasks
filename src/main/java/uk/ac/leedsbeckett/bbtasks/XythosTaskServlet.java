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
import com.amazonaws.auth.AWSCredentialsProvider;
import com.xythos.common.AwsCloudStorageConfigImpl;
import com.xythos.common.CloudStorageLocationImpl;
import com.xythos.common.api.CloudStorageConfig;
import com.xythos.common.api.CloudStorageLocation;
import com.xythos.common.api.NetworkAddress;
import com.xythos.common.api.StorageLocation;
import com.xythos.common.api.VirtualServer;
import com.xythos.fileSystem.Revision;
import com.xythos.security.ContextImpl;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import uk.ac.leedsbeckett.bbtasks.tasks.AnalyseVideoTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosAnalyseAutoArchiveTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosAnalyseDeletedAutoArchiveTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosArchiveHugeCourseFilesAnalysis;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosListDeletedFilesTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosMoveHugeCourseFilesTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosArchiveHugeCourseFilesStageOneTask;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosArchiveHugeCourseFilesStageTwoTask;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

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

    String s3 = req.getParameter("s3");
    if ( s3 != null && s3.length() > 0 )
    {
      doS3( req, resp );
      return;
    }

    String aatest = req.getParameter("aatest");
    if ( aatest != null && aatest.length() > 0 )
    {
      doAATest( req, resp );
      return;
    }

    String movehugecoursefiles = req.getParameter( "movehugecoursefiles" );
    if ( movehugecoursefiles != null && movehugecoursefiles.length() > 0 )
    {
      doMoveHugeCourseFiles( req, resp );
      return;
    }
    
    String archivehugecoursefilesstageone = req.getParameter( "archivehugecoursefilesstageone" );
    if ( archivehugecoursefilesstageone != null && archivehugecoursefilesstageone.length() > 0 )
    {
      doArchiveHugeCourseFilesStageOne( req, resp );
      return;
    }
    
    String archivehugecoursefilesstagetwo = req.getParameter( "archivehugecoursefilesstagetwo" );
    if ( archivehugecoursefilesstagetwo != null && archivehugecoursefilesstagetwo.length() > 0 )
    {
      doArchiveHugeCourseFilesStageTwo( req, resp );
      return;
    }
    
    String archivehugecoursefilesanalysis = req.getParameter( "archivehugecoursefilesanalysis" );
    if ( archivehugecoursefilesanalysis != null && archivehugecoursefilesanalysis.length() > 0 )
    {
      doArchiveHugeCourseFilesAnalysis( req, resp );
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

    if ( y<2020 || y>2100 || y2<2020 || y2>2100 )
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

  protected void doS3(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>S3 Test</h1>" );
      /*
      try
      {
        //listStorageLocationParameters( out );

        
        CloudStorageLocation csl = CloudStorageLocationImpl.findLocation(1101);
        if ( csl == null )
          out.println( "<p>Storage Loction Not Found.</p>" );
        else
        {
          CloudStorageConfig config = csl.getCopyOfConfig();
          out.println( "<p>Config class = " + config.getClass() + "</p>" );
          if ( config instanceof AwsCloudStorageConfigImpl )
          {
            AwsCloudStorageConfigImpl awsconfig = (AwsCloudStorageConfigImpl)config;
            out.println( "<p>AccessID = " + awsconfig.getAccessId() + "</p>" );
            String strcredprov = awsconfig.getCustomCredentialsProviderClass();
            out.println( "<p>Custom provider class = " + strcredprov + "</p>" );
            if (StringUtils.isNotBlank((CharSequence)strcredprov))
            {
              final Class l_providerClass = Class.forName(strcredprov);
              AWSCredentialsProvider credprov = (AWSCredentialsProvider)l_providerClass.getDeclaredConstructor((Class<?>[])new Class[0]).newInstance(new Object[0]);
              out.println( "<p>Access ID  = " + credprov.getCredentials().getAWSAccessKeyId() + "</p>" );
              out.println( "<p>Secret Key = " + credprov.getCredentials().getAWSSecretKey() + "</p>" );
            }
          }
        }
        out.println( "<p>No errors reported.</p>" );
      }
      catch ( Exception ex )
      {
        ex.printStackTrace( new PrintWriter( out ) );
      }
      */
      out.println( "</body></html>" );      
    }      
  }

  
  protected void doAATest(HttpServletRequest req, HttpServletResponse resp )
          throws ServletException, IOException
  {
    String path = req.getParameter( "path" );
    
    webappcore.logger.info( "doAATest" );
    
    resp.setContentType("text/html; charset=UTF-8");
    resp.setCharacterEncoding("UTF-8");
    try ( PrintWriter out = resp.getWriter(); )
    {        
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"../index.html\">Home</a></p>" );      
      out.println( "<h1>Autoarchive test</h1>" );
      out.println( "<pre><tt>" );
      out.println( "Autoarchive test process started. " ); 

      FileSystemEntry entry = null;
      StringBuilder message = new StringBuilder();
      ContextImpl l_adminContext = null;
      try
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        l_adminContext = (ContextImpl)AdminUtil.getContextForAdmin("AnalyseAutoArchiveThread:285");        
        
        entry = FileSystem.findEntry(vs, path, false, l_adminContext);
        if ( entry == null )
          throw new Exception( "Could not find entry." );

        if ( !(entry instanceof com.xythos.fileSystem.File) )
          throw new Exception( "Entry not a file." );

        FileSystemEntry f = entry;
        LocalDateTime dt = f.getCreationTimestamp().toLocalDateTime();
        int fdatecode = dt.getYear() * 10000 + dt.getMonthValue()*100 + dt.getDayOfMonth();
        out.println( "\"" + f.getName() + "\"," + f.getFileContentType() + "," + fdatecode + "," + f.getEntrySize() ); 
        com.xythos.fileSystem.File file = (com.xythos.fileSystem.File)f;
        out.println( "file.getEntrySize = " + file.getEntrySize() );
        out.println( "file.getSizeOfFileVersion = " + file.getSizeOfFileVersion() );
        Revision r = file.getLatestRevision();        
        out.println( "r.getSize = " + r.getSize() );
        out.println( "blobid = " + r.getBlobID() );
        StorageLocation sl = r.getStorageLocation();
        out.println( "StorageLocation = " + sl.getClass() );
        
        BlobSearchResult bsr = new BlobSearchResult();
        out.println( "About to analyse the zip file." );
        LocalXythosUtils.analyseZip( file, bsr, true );
        out.println( bsr.message.toString() );
        out.println( "Done" );
      }
      catch ( Exception e )
      {
        webappcore.logger.error("Exception: ", e );
      } 
      finally
      {
        out.println( "Start of finally." );
        webappcore.logger.info( "doAATest 'finally'" );
        if ( l_adminContext != null )
        {
          try { l_adminContext.commitContext(); } catch ( Exception ex )
          { 
            out.println( "Error attempt to commit context: " + ex.toString() );
            webappcore.logger.error("Can't commit context", ex );
          }
        }
        out.println( "end of 'finally'" );
        webappcore.logger.info( "doAATest 'finally end'" );
      }
      out.println( "<tt><pre><hr />" );
      out.println( "</body></html>" );      
    }
    webappcore.logger.info( "doAATest completed" );
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
      out.println( "<h1>Xythos List Deleted Files Task</h1>" );
      
      try
      {
        webappcore.requestTask( new XythosListDeletedFilesTask() );
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
      try
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosAnalyseAutoArchiveTask( vs.getName(), y, m, d, y2, m2, d2 ) );
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

  
  protected void doMoveHugeCourseFiles(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Analyse Video</h1>" );
      try
      {
        String regex = req.getParameter( "regex" );
        if ( regex==null || regex.isBlank() ) regex = "[0-9]*\\.[0-9]4";
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosMoveHugeCourseFilesTask( vs.getName(), regex ) );
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
  
  protected void doArchiveHugeCourseFilesStageOne(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Archive Huge Course Files Stage One</h1>" );
      try
      {
        String strblobidlist = req.getParameter( "blobidlist" );
        String[] blobidlistlines = strblobidlist.split( "\n" );
        ArrayList<Long> blobids = new ArrayList<>();
        for ( String line : blobidlistlines )
        {
          String[] fields = line.trim().split(",");
          if ( fields.length > 0 && fields[0].matches( "[0-9]*" ) )
            blobids.add( Long.parseLong( fields[0]) );
        }
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosArchiveHugeCourseFilesStageOneTask( vs.getName(), blobids ) );
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

  protected void doArchiveHugeCourseFilesStageTwo(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Archive Huge Course Files Stage Two</h1>" );
      try
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosArchiveHugeCourseFilesStageTwoTask( vs.getName() ) );
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
  
  protected void doArchiveHugeCourseFilesAnalysis(HttpServletRequest req, HttpServletResponse resp )
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
      out.println( "<h1>Archive Huge Course Files Analysis</h1>" );
      try
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosArchiveHugeCourseFilesAnalysis( vs.getName() ) );
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
      out.println( "<h1>Analyse Video</h1>" );
      try
      {
        String action = req.getParameter( "action" );
        String regex = req.getParameter( "regex" );
        if ( regex==null || regex.isBlank() ) regex = "/.*";
        String reset = req.getParameter( "reset" );
        String option = StringUtils.isBlank( reset )?"":"reset";
        webappcore.requestTask( new AnalyseVideoTask( req.getServerName(), action, option, regex ) );
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
      try
      {
        VirtualServer vs = NetworkAddress.findVirtualServer(req);
        webappcore.requestTask( new XythosAnalyseDeletedAutoArchiveTask( vs.getName(), y, m, d, y2, m2, d2 ) );
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
