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

package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import blackboard.platform.contentsystem.service.ContentSystemServiceExFactory;
import com.xythos.common.InternalException;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.common.dbConnect.JDBCConnection;
import com.xythos.common.dbConnect.JDBCResultSetWrapper;
import com.xythos.fileSystem.BinaryObject;
import com.xythos.fileSystem.Revision;
import com.xythos.security.ContextImpl;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.ServerGroupImpl;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.admin.api.ServerGroup;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import uk.ac.leedsbeckett.bbtasks.BlobSearchResult;
import uk.ac.leedsbeckett.bbtasks.Signatures;
import uk.ac.leedsbeckett.bbtasks.TaskException;
import uk.ac.leedsbeckett.bbtasks.XythosAdapterChannel;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosArchiveHugeCourseFilesStageTwoTask;

/**
 *
 * @author jon
 */
public class LocalXythosUtils
{  
  private static void analyseZip( BinaryObject zip, BlobSearchResult bsr, boolean verbose ) throws XythosException, IOException, InterruptedException
  {
    //bbmonitor.logger.info( "Start of zip analysis of blob. " + zip.getBlobID() );
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
      analyseZip( zipfile, bsr, verbose );
      bsr.iszip = true;
    }
    catch ( ZipException ze )
    {
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
  
  public static void analyseZip( com.xythos.fileSystem.File zip, BlobSearchResult bsr, boolean verbose ) throws XythosException, IOException, InterruptedException
  {
    //bbmonitor.logger.info( "Start of zip analysis of file revision. " + zip.getName() + "(" + zip.getEntrySize() + "bytes)" );
    XythosAdapterChannel channel = null;
    ZipFile zipfile = null;
    try
    {
      Revision r = zip.getLatestRevision();
      if ( verbose )
      {
        bsr.message.append( "zip.getEntrySize = " + zip.getEntrySize() + "\n" );
        bsr.message.append( "zip.getSizeOfFileVersion = " + zip.getSizeOfFileVersion() + "\n" );
        bsr.message.append( "r.getSize = " + r.getSize() + "\n" );
      }
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
      analyseZip( zipfile, bsr, verbose );
    }    
    finally
    {
      if ( zipfile != null )
        zipfile.close();
      if ( channel != null )
        channel.close();
    }
  }
  
  private static void analyseZip( ZipFile zipfile, BlobSearchResult bsr, boolean verbose ) 
          throws XythosException, IOException, InterruptedException
  {
    Enumeration<ZipArchiveEntry> e = zipfile.getEntries();
    while ( e.hasMoreElements() )
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      ZipArchiveEntry entry = e.nextElement();
      String name = entry.getName();
      bsr.totalusage += entry.getSize();
      //bbmonitor.logger.info( "Name = " + name );
      if ( name.startsWith( "ppg/BB_Direct/Uploads/" ))
        bsr.turnitinusage += entry.getSize();
      if ( name.startsWith( "csfiles/" ))
        bsr.csfilesusage += entry.getSize();
      if ( verbose )
      {
        bsr.message.append( entry.getSize() );
        bsr.message.append( ",\"" );
        bsr.message.append( entry.getName() );
        bsr.message.append( "\"\n" );
      }
    }
    //bbmonitor.logger.info( "End of zip analysis." );
  }

  public static String[] getFileSystems() throws XythosException {
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
    
  public static void findBinaryObjects(final JDBCConnection p_conn, List<BlobSearchResult> list, int y, int m, int d, int y2, int m2, int d2 ) throws SQLException, InternalException
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

  public static void listBinaryObjects(final JDBCConnection p_conn, PrintWriter writer ) throws SQLException, InternalException
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
  
  public static void getBinaryObject(final JDBCConnection p_conn, BlobSearchResult bsr ) throws SQLException, InternalException, IOException, XythosException, InterruptedException
  {
    BinaryObject blob = null;
    try
    {
      blob = BinaryObject.getExpectedBinaryObject(p_conn, bsr.id, bsr.storagestate );
      bsr.refcount = blob.getRefCount();
      bsr.size = blob.getSize();
      bsr.storagefilename = blob.getStorageName();
      bsr.tempstoragefilename = blob.getTemporaryStorageName();
      try ( ByteArrayOutputStream baos = new ByteArrayOutputStream(); )
      {
        if ( bsr.size >= 4 )
          blob.getBytes(baos, 0, 4, false, false, false, false, null, null, null, true);
        bsr.signature = baos.toByteArray();
      }
      bsr.iszip = Signatures.isZip( bsr.signature );
      bsr.created = blob.getStorageDate();
      if ( bsr.iszip )
        analyseZip( blob, bsr, false );
    }
    finally
    {
//      if ( blob != null )
//        blob.closeAfterRead(p_context);
    }
  }
 
  
  
  
  public static BlobInfoMap getArchivedHugeBinaryObjects( VirtualServer vs ) throws SQLException, InternalException, XythosException
  {
    BlobInfoMap  bimap= new BlobInfoMap();
    
    ContextImpl context = (ContextImpl)AdminUtil.getContextForAdmin( "getArchivedHugeBinaryObjects" );
    JDBCConnection p_conn = context.getFileSystemConnection( vs, FileSystemUtil.getTopLevelDirectory( "/institution/") );

    PreparedStatement l_stmt = null;
    ResultSet l_rset = null;
    final String l_sql = 
            "SELECT uu.full_path, vv.blob_id, bb.blob_size, vv.file_id " +
            "FROM xyf_urls uu, xyf_files ff, xyf_file_versions vv, xyf_blobs bb " +
            "WHERE uu.file_id = vv.file_id AND vv.file_id = ff.file_id AND vv.blob_id = bb.blob_id " +
            "AND bb.blob_size > 10000000 " +
            "AND vv.blob_id IN " +
              "(SELECT DISTINCT v.blob_id FROM xyf_urls u, xyf_files f, xyf_file_versions v " +
                "WHERE u.file_id = v.file_id AND v.file_id = f.file_id " +
                "AND f.mime_type like 'video/%' AND u.full_path LIKE '/courses/%') " +
            "ORDER BY vv.blob_id, uu.full_path";
    
    try
    {
      l_stmt = p_conn.prepareStatement(l_sql);
      l_rset = l_stmt.executeQuery();
      for ( int i=0; l_rset.next(); i++ )
        bimap.addBlobInfo( new FileVersionInfo( 
                                          l_rset.getString( 1 ), 
                                          l_rset.getLong( 2 ), 
                                          l_rset.getLong( 3 ),
                                          l_rset.getLong( 4 )
                                       )                               );
    }
    finally
    {
      if (l_rset != null)
      {
        try { l_rset.close(); }
        catch (SQLException ex) {}
      }
      if (l_stmt != null)
      {
        try { l_stmt.close(); }
        catch (SQLException ex2) {}
      }
    }
    
    return bimap;
  }
  
}
