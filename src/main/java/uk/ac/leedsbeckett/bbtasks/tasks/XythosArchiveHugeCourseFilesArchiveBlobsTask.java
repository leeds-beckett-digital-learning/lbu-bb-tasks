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

package uk.ac.leedsbeckett.bbtasks.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.DirectoryEntry;
import com.xythos.fileSystem.File;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.CreateDirectoryData;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemUtil;
import com.xythos.storageServer.api.StorageServerException;
import com.xythos.storageServer.permissions.api.AccessControlEntry;
import com.xythos.storageServer.permissions.api.DirectoryAccessControlEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import uk.ac.leedsbeckett.bbtasks.TaskException;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfoMap;
import uk.ac.leedsbeckett.bbtasks.xythos.BuilderInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.CourseInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.FileVersionInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LinkInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 * * @author jon
 */
public class XythosArchiveHugeCourseFilesArchiveBlobsTask extends BaseTask
{
  public static final String TARGET_BASE = "/institution/hugefiles";
  public static final String PLACEHOLDER = "/institution/hugefiles/placeholder.mp4";
  //public static final long FILE_SIZE_THRESHOLD = (600L*1024L*1024L);
  
  public String virtualservername;
  public String method;
  public ArrayList<Long> blobids;

  private transient VirtualServer vs;
  
  @JsonCreator
  public XythosArchiveHugeCourseFilesArchiveBlobsTask( 
          @JsonProperty("virtualservername") String virtualservername, 
          @JsonProperty("method")            String method, 
          @JsonProperty("blobids")           ArrayList<Long> blobids )
  {
    this.virtualservername = virtualservername;
    this.method = method;
    this.blobids = blobids;
  }

  @Override
  public void doTask() throws InterruptedException
  {
//    NumberFormat nf = NumberFormat.getIntegerInstance();
//    SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd" );
//    SimpleDateFormat japanformat = new SimpleDateFormat( "yyyyMMdd" );
    vs = VirtualServer.find( virtualservername );
    Path logfile = webappcore.logbase.resolve( "hugecoursefiles-directory-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
    Path emailfile = webappcore.logbase.resolve( "hugecoursefiles-deletion-emails-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to move huge course files. This may take many minutes." );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
      return;
    }

    
    debuglogger.info( "Starting to archive huge course files. " ); 
    debuglogger.info( "Virtual server " + virtualservername ); 
    //long start = System.currentTimeMillis();
    BlobInfoMap bimap;
    try
    {
      bimap = LocalXythosUtils.getArchivedHugeBinaryObjects( debuglogger, vs, this.blobids );
      bimap.addBlackboardInfo();
      debuglogger.info( "Done." );
    }
    catch ( XythosException | InterruptedException | SQLException ex )
    {
      debuglogger.error( "Unable to complete analysis.", ex );
      return;
    }
    

    // copy them all
    try
    {
      for ( BlobInfo bi : bimap.blobs )
        for ( FileVersionInfo vi : bi.getFileVersions() )
          copyOneHugeFile( vi );
    }
    catch ( TaskException | XythosException tex )
    {
      debuglogger.error( "Error while attempting to create archive directories.", tex );
      return;
    }    
    
    // if fully successful, delete/overwrite the originals
    try
    {
      for ( BlobInfo bi : bimap.blobs )
        for ( FileVersionInfo vi : bi.getFileVersions() )
        {
          if ( "replace".equalsIgnoreCase( method ) )
            overwriteOneHugeFile( vi, "/institution/hugefiles/videoarchivedmessage.mp4"  );
          else
            deleteOneHugeFile( vi );
        }
    }
    catch ( TaskException | XythosException tex )
    {
      debuglogger.error( "Error while attempting to delete/overwrite archived video files.", tex );
      return;
    }    

    // Output the file listing file in CSV format
    try ( Writer writer = new FileWriter( logfile.toFile() );
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT ))
    {
      for ( BlobInfo bi : bimap.blobs )
        for ( FileVersionInfo vi : bi.getFileVersions() )
          csvPrinter.printRecord( bi.getBlobId(), bi.getDigest(), bi.getLinkCount(), vi.getCopyUrl(), vi.getPath() );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
      return;
    }

    int emailcount = 1;
    try ( PrintWriter log = new PrintWriter( new FileWriter( emailfile.toFile() ) ); )
    {
      for ( BuilderInfo  builder : bimap.builders )
      {
        if (Thread.interrupted())
        {
          log.println( "Task interrupted, output terminated." );
          break;
        }
        
        List<CourseInfo> relevantcourses = builder.getCoursesWithFiles();
        if ( relevantcourses.isEmpty() )
          continue;
                
        log.println( "----Start Email-----------------" );
        log.println( ":ID:" + emailcount++ );
        log.println( ":Name:" + builder.getName() );
        log.println( ":Address:" + builder.getEmail() );
        log.println( ":Subject:Deleted Videos in My Beckett" );
        log.println();
        for ( CourseInfo course : relevantcourses )
        {
          log.println( "<h3>" + course.getTitle() + "</h3>" );
          log.println( "<ol>" );
          for ( FileVersionInfo file : course.getFiles() )
          {
            log.println( "<li><a href=\"https://my.leedsbeckett.ac.uk/bbcswebdav/" + file.getCopyUrl() + "\">" + file.getPath() + "</a><br />" );
            log.println( "<em style=\"font-size: 75%;\">Check sum " + file.getDigest() + "</em></li>" );
          }
          log.println( "</ol>" );
        }
        log.println( "----End Email-----------------" );
        log.println();
        log.println();
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file - emails.", ex );
    }
    
  }  
  
  void createArchiveFolder( Context context, String dirname ) throws TaskException, XythosException
  {
    String targetname = TARGET_BASE + "/" + dirname;
    FileSystemEntry targetentry;

    debuglogger.info( "Creating " + targetname );

    targetentry = FileSystem.findEntry( vs, targetname, false, context );
    if ( targetentry != null && (targetentry instanceof FileSystemDirectory) )
      return; // because it already exists

    if ( targetentry != null  )
    {
      String message = "Target directory name blocked by another type of file system entry: " + dirname + " " + targetentry.getClass() + ".\n";
      debuglogger.error( message );
      throw new TaskException( message );
    }

    // It doesn't exist, nothing is blocking it so create it now:
    CreateDirectoryData cdc = new CreateDirectoryData( vs, TARGET_BASE, dirname,  context.getContextUser().getPrincipalID() );
    FileSystemDirectory fsd = FileSystem.createDirectory( cdc, context );
    debuglogger.info( "Created (but not committed)." + fsd.getName() );

    AccessControlEntry[] courseacearray = fsd.getPrincipalAccessControlEntries();
    for ( AccessControlEntry ace : courseacearray )
    {
      //debuglogger.info( ace.getPrincipalID() );
      if ( "G:IR:STAFF".equals( ace.getPrincipalID() ) )
        fsd.deleteAccessControlEntry( "G:IR:STAFF" );
    }

    String courseinstructorrole = CourseInfo.getCourseInstructorRole( dirname );
    DirectoryAccessControlEntry courseace = (DirectoryAccessControlEntry)fsd.getAccessControlEntry( courseinstructorrole );
    if ( courseace == null )
    {
      String message =  "Couldn't obtain DirectoryAccessControlEntry for " + courseinstructorrole + " on " + fsd.getName();
      debuglogger.error( message );
      throw new TaskException( message );
    }
    courseace.setReadable( true );
    courseace.setChildInheritReadable( true );
    fsd.recursivePermissionsOverwrite();      
    
    debuglogger.info( "Committed." );    
  }

  void copyOneHugeFile( FileVersionInfo vi ) throws StorageServerException, XythosException, TaskException
  {
    Context context=null;
    if ( !vi.getPath().startsWith( "/courses/" ) )
      throw new TaskException( "Can only move/copy files in /courses/ top level directory." );
    
    try
    {
      
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {        
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      
      FileSystemEntry coursefile = FileSystem.findEntry( vs, vi.getPath(), false, context );
    
      debuglogger.info( "Copying " + coursefile.getName() );
      DirectoryEntry de = (DirectoryEntry)coursefile;
      File f = (File)coursefile;
      int version = f.getFileVersion();

      String relativepath = coursefile.getName().substring( "/courses".length() );
      String destinationpath = TARGET_BASE + relativepath;

      debuglogger.info( "Copying to " + destinationpath );

      String[] parts = destinationpath.split( "/" );
      String partial = "";
      String previouspartial = "";
      for ( int i=1; i<parts.length; i++ )
      {
        partial = partial + "/" + parts[i];
        if ( i< (parts.length-1) )
        {
          if ( i==3 )
            createArchiveFolder( context, parts[3] );
          
          if ( i>=4 )
          {
            // check/create a subdirectory
            if ( FileSystem.findEntry( vs, partial, false, context ) == null )
            {
              //debuglogger.info( "Check/create " + parts[i] + " in " + previouspartial );
              if ( FileSystemUtil.isValidEntryName( parts[i] ) )
              {
                CreateDirectoryData cdc = new CreateDirectoryData( vs, previouspartial, parts[i], coursefile.getCreatedByPrincipalID() );
                FileSystem.createDirectory( cdc, context );
              }
              else
                debuglogger.info( "Name " + parts[i] + " is invalid." );              
            }
          }
          previouspartial = partial;        
        }
        else
        {
          //debuglogger.info( "copying actual file " + parts[i] + " to " + previouspartial );
          DirectoryEntry newentry = de.copyNode( 
                  version,                       // version number of source to copy
                  vs,                            // virtual server
                  previouspartial,               // destination dir
                  parts[i],                      // (new) name
                  de.getCreatedByPrincipalID(),  // same owner as source
                  2,                             // weddav depth - 2 means infinite which is default depth
                  false,                         // overwrite 
                  DirectoryEntry.TRASH_OP.NONE,  // no trash operation
                  false                          // not move, copy
          );
          vi.setCopyURL( "xid-" + newentry.getEntryID() );
//          debuglogger.info( "ID             " + newentry.getID() );
//          debuglogger.info( "entry ID       " +  );
//          debuglogger.info( "local entry ID " + newentry.getLocalEntryID() );
//          debuglogger.info( "path ID        " + newentry.getPathID() );
        }
      }
    }
    catch ( XythosException th )
    {
      debuglogger.error( "Error occured looking for files in course directory.", th);
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to roll back Xythos context.", ex ); }
      }
    }
    finally
    {
      if ( context != null )
      {
       try { context.commitContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to commit Xythos context.", ex ); }
      }
    }
  }


  void deleteOneHugeFile( FileVersionInfo vi ) throws StorageServerException, XythosException, TaskException
  {
    Context context=null;
    if ( !vi.getPath().startsWith( "/courses/" ) )
      throw new TaskException( "Can only delete files in /courses/ top level directory." );
    
    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }      
      FileSystemEntry coursefile = FileSystem.findEntry( vs, vi.getPath(), false, context );
      debuglogger.info( "Deleting " + coursefile.getName() );
      coursefile.delete();
    }
    catch ( XythosException th )
    {
      debuglogger.error( "Error occured looking for files in course directory.", th);
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to roll back Xythos context.", ex ); }
      }
    }
    finally
    {
      if ( context != null )
      {
       try { context.commitContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to commit Xythos context.", ex ); }
      }
    }
  }

  void overwriteOneHugeFile( FileVersionInfo vi, String sourcepath ) throws StorageServerException, XythosException, TaskException
  {
    Context context=null;
    if ( !vi.getPath().startsWith( "/courses/" ) )
      throw new TaskException( "Can only overwrite files in /courses/ top level directory." );
    
    try
    {
      
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      
      String path = vi.getPath();
      int n = path.lastIndexOf( "/" );
      String destinationdir = path.substring( 0, n );
      String destinationname = path.substring( n+1 );
      
      
      FileSystemEntry sourcefile = FileSystem.findEntry( vs, sourcepath, false, context );
    
      debuglogger.info( "Copying " + sourcefile.getName() + " over " + vi.getPath() + " File ID " + vi.getFileId() );
      DirectoryEntry de = (DirectoryEntry)sourcefile;
      File f = (File)sourcefile;
      int version = f.getFileVersion();

      //debuglogger.info( "copying actual file " + parts[i] + " to " + previouspartial );
      DirectoryEntry newentry = de.copyNode( 
              version,                       // version number of source to copy
              vs,                            // virtual server
              destinationdir,                // destination dir
              destinationname,               // (new) name
              de.getCreatedByPrincipalID(),  // same owner as source
              2,                             // webdav depth - 2 means infinite which is default depth
              true,                          // overwrite 
              DirectoryEntry.TRASH_OP.NONE,  // no trash operation
              false                          // not move, copy
      );
      
      debuglogger.info( "ID             " + newentry.getID() );
      debuglogger.info( "entry ID       " + newentry.getEntryID() );
      debuglogger.info( "local entry ID " + newentry.getLocalEntryID() );
      debuglogger.info( "path ID        " + newentry.getPathID() );

      // Renaming is done with 'move' using same parent directory
      // This should preserve the file ID and should not break links to
      // the original.
      newentry.move( destinationdir, "archive_stub_" + destinationname, false );

      debuglogger.info( "ID             " + newentry.getID() );
      debuglogger.info( "entry ID       " + newentry.getEntryID() );
      debuglogger.info( "local entry ID " + newentry.getLocalEntryID() );
      debuglogger.info( "path ID        " + newentry.getPathID() );
    }
    catch ( XythosException th )
    {
      debuglogger.error( "Error occured looking for files in course directory.", th);
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to roll back Xythos context.", ex ); }
      }
    }
    finally
    {
      if ( context != null )
      {
       try { context.commitContext(); }
        catch ( XythosException ex ) { debuglogger.error( "Failed to commit Xythos context.", ex ); }
      }
    }
  }

  
}
