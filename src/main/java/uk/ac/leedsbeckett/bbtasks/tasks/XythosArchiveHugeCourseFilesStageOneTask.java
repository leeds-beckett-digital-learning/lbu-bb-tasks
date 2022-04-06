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

import uk.ac.leedsbeckett.bbtasks.tasks.data.XythosDirectoryInfo;
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
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import uk.ac.leedsbeckett.bbtasks.TaskException;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfoMap;
import uk.ac.leedsbeckett.bbtasks.xythos.CourseInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.FileVersionInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LinkInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 *
 * @author jon
 */
public class XythosArchiveHugeCourseFilesStageOneTask extends BaseTask
{
  public static final String TARGET_BASE = "/institution/hugefiles";
  public static final String PLACEHOLDER = "/institution/hugefiles/placeholder.mp4";
  public static final long FILE_SIZE_THRESHOLD = (600L*1024L*1024L);
  
  public String virtualservername;
  public ArrayList<Long> blobids;

  private transient VirtualServer vs;
  
  @JsonCreator
  public XythosArchiveHugeCourseFilesStageOneTask( 
          @JsonProperty("virtualservername") String virtualservername, 
          @JsonProperty("blobids")           ArrayList<Long> blobids )
  {
    this.virtualservername = virtualservername;
    this.blobids = blobids;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    NumberFormat nf = NumberFormat.getIntegerInstance();
    SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd" );
    SimpleDateFormat japanformat = new SimpleDateFormat( "yyyyMMdd" );
    vs = VirtualServer.find( virtualservername );
    Path logfile = webappcore.logbase.resolve( "archivehugecoursefiles-stage1" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to move huge course files. This may take many minutes." );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
      return;
    }

    
    debuglogger.info( "Starting to analyse archived huge course files. " ); 
    debuglogger.info( "Virtual server " + virtualservername ); 
    long start = System.currentTimeMillis();
    BlobInfoMap bimap=null;
    try
    {
      bimap = LocalXythosUtils.getArchivedHugeBinaryObjects( vs, this.blobids );
      bimap.addBlackboardInfo();
      debuglogger.info( "Done." );
    }
    catch ( Exception ex )
    {
      debuglogger.error( "Unable to complete analysis.", ex );
    }
    
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      long totalunlinkedbytes = 0L;
      long totalbytes = 0L;
      for ( BlobInfo bi : bimap.blobs )
      {
        totalbytes += bi.getSize();
        log.print( "blobid = " + bi.getBlobId() + ", " + nf.format(bi.getSize()/1000000) + "MiB,  run tot "  + nf.format(totalbytes/1000000) + "MiB" );
        log.println( (bi.getLastAccessed() == null)?"":", Most Recent Module Access =              " + sdf.format(bi.getLastAccessed()) );
        if ( bi.getLinkCount() == 0 )
          totalunlinkedbytes += bi.getSize();
        for ( FileVersionInfo vi : bi.getFileVersions() )
        {
          log.println( "    File  " + vi.getPath() ); // + ", " + vi.getFileId() );
          for ( LinkInfo link :  bimap.linklistmap.get( vi.getStringFileId() ) )
          {
            CourseInfo ci = bimap.getCourseInfo( link.getLink().getCourseId() );
            log.print( "        Course " + link.getLink().getCourseId().toExternalString() + ", " + link.getLink().getParentDisplayName() );
            log.println( (ci==null || ci.getLastAccessed() == null)?"":",              " + sdf.format(ci.getLastAccessed()) );
          }
        }
      }
      log.println();
      log.println();
      for ( BlobInfo bi : bimap.blobs )
      {
        log.print( bi.getBlobId() + "," + bi.getSize()/1000000 + ","  );
        log.println( (bi.getLastAccessed() == null)?"0":japanformat.format(bi.getLastAccessed()) );
      }
      log.println();
      log.println();
      log.println( "Total          " + nf.format( totalbytes ) );
      log.println( "Total unlinked " + nf.format( totalunlinkedbytes ) );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
    
/*    
    ArrayList<XythosDirectoryInfo> courselist = null;
    ArrayList<XythosDirectoryInfo> interestinglist = null;
    ArrayList<NotificationRecipientInfo> recipientlist = null;
    try
    {
      courselist = getListOfCourseFolders();
      interestinglist = getListOfInterestingCourseFolders( courselist );
      for ( XythosDirectoryInfo n : interestinglist )
        createArchiveFolder( n );
      for ( XythosDirectoryInfo n : interestinglist )
        copyHugeFiles( n );
    }
    catch ( TaskException te )
    {
      debuglogger.error( "Task failed. ", te );     
    }


    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      if ( courselist != null && interestinglist != null)
      {
        for ( XythosDirectoryInfo n : courselist )
        {
          log.print( n );
          if ( interestinglist.contains( n ) )
            log.print( "  INTERESTING" );
          log.println();
        }
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
  */  
    
  }  
  

  ArrayList<XythosDirectoryInfo> getListOfInterestingCourseFolders( List<XythosDirectoryInfo> names ) throws InterruptedException, TaskException
  {
    ArrayList<XythosDirectoryInfo> courselist = new ArrayList<>();
    for ( XythosDirectoryInfo coursename : names )
    {
      // yield if someone is trying to interrupt this task
      if ( Thread.interrupted() ) throw new InterruptedException();
      if ( isInterestingCourseFolder( coursename ) )
        courselist.add( coursename );
    }
    return courselist;
  }    
  
  boolean isInterestingCourseFolder( XythosDirectoryInfo source ) throws TaskException
  {
    Context context=null;
    FileSystemEntry baseentry;

    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      
      baseentry = FileSystem.findEntry( vs, source.getName(), false, context );
      if ( baseentry == null || !(baseentry instanceof FileSystemDirectory) )
      {
        debuglogger.error( "Could not find " + source + " directory.\n" );
        throw new TaskException( "Could not find " + source + " directory.\n" );
      }
      FileSystemDirectory basedir = (FileSystemDirectory)baseentry;

      FileSystemEntry[] coursefiles = basedir.getDirectoryContentsRecursive( false, 100000 );
      for ( FileSystemEntry coursefile : coursefiles )
      {
        if (   coursefile != null &&
               coursefile.getFileContentType() != null &&
               coursefile.getFileContentType().startsWith( "video/" ) &&
               coursefile.getTotalSize() > FILE_SIZE_THRESHOLD )
          return true;
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

    return false;
  }

  void createArchiveFolder( XythosDirectoryInfo source ) throws TaskException
  {
    Context context=null;
    String dname = source.getCourseId();
    String targetname = TARGET_BASE + "/" + dname;
    FileSystemEntry targetentry;

    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      targetentry = FileSystem.findEntry( vs, targetname, false, context );
      if ( targetentry != null && (targetentry instanceof FileSystemDirectory) )
        return; // because it already exists
      
      if ( targetentry != null  )
      {
        String message = "Target directory name blocked by another type of file system entry: " + source + " " + targetentry.getClass() + ".\n";
        debuglogger.error( message );
        throw new TaskException( message );
      }

      // It doesn't exist, nothing is blocking it so create it now:
      CreateDirectoryData cdc = new CreateDirectoryData( vs, TARGET_BASE, dname, source.getPrincipal());
      FileSystemDirectory fsd = FileSystem.createDirectory( cdc, context );
      debuglogger.info( "Created (but not committed)." + fsd.getName() );

      AccessControlEntry[] courseacearray = fsd.getPrincipalAccessControlEntries();
      for ( AccessControlEntry ace : courseacearray )
      {
        debuglogger.info( ace.getPrincipalID() );
        if ( "G:IR:STAFF".equals( ace.getPrincipalID() ) )
          fsd.deleteAccessControlEntry( "G:IR:STAFF" );
      }
      
      String courseinstructorrole = source.getCourseInstructorRole();
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
    }
    catch ( XythosException th )
    {
      debuglogger.error( "Error occured creating archive folder", th);
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
    
    debuglogger.info( "Committed." );    
  }

  void copyHugeFiles( XythosDirectoryInfo source ) throws TaskException
  {
    Context context=null;
    FileSystemEntry sourceentry;
    String dname = source.getName().substring( source.getName().lastIndexOf( '/' )+1 );
    String targetname = TARGET_BASE + "/" + dname;
    FileSystemEntry targetentry;
    String subname;

    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      
      sourceentry = FileSystem.findEntry( vs, source.getName(), false, context );
      if ( sourceentry == null || !(sourceentry instanceof FileSystemDirectory) )
      {
        debuglogger.error( "Could not find " + source + " directory.\n" );
        throw new TaskException( "Could not find " + source + " directory.\n" );
      }
      FileSystemDirectory basedir = (FileSystemDirectory)sourceentry;

      FileSystemEntry[] coursefiles = basedir.getDirectoryContentsRecursive( false, 100000 );
      for ( FileSystemEntry coursefile : coursefiles )
      {
        if (   coursefile != null &&
               coursefile.getFileContentType() != null &&
               coursefile.getFileContentType().startsWith( "video/" ) &&
               coursefile.getTotalSize() > FILE_SIZE_THRESHOLD )
          copyOneHugeFile( context, coursefile );
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

  
  void copyOneHugeFile( Context context, FileSystemEntry coursefile ) throws StorageServerException, XythosException
  {
    debuglogger.info( "Copying " + coursefile.getName() + " class " + coursefile.getClass() );
    DirectoryEntry de = (DirectoryEntry)coursefile;
    File f = (File)coursefile;
    int version = f.getFileVersion();
    
    String destinationpath = TARGET_BASE + coursefile.getName().substring( "/courses".length() );
    
    debuglogger.info( "Copying to " + destinationpath );
    
    String[] parts = destinationpath.split( "/" );
    String partial = "";
    String previouspartial = "";
    for ( int i=1; i<parts.length; i++ )
    {
      partial = partial + "/" + parts[i];
      if ( i< (parts.length-1) )
      {
        if ( i>=4 )
        {
          // check/create a subdirectory
          if ( FileSystem.findEntry( vs, partial, false, context ) == null )
          {
            debuglogger.info( "Check/create " + parts[i] + " in " + previouspartial );
            if ( FileSystemUtil.isValidEntryName( parts[i] ) )
            {
              CreateDirectoryData cdc = new CreateDirectoryData( vs, previouspartial, parts[i], coursefile.getCreatedByPrincipalID() );
              FileSystemDirectory fsd = FileSystem.createDirectory( cdc, context );
            }
            else
              debuglogger.info( "Name " + parts[i] + " is invalid." );              
          }
        }
        previouspartial = partial;        
      }
      else
      {
        debuglogger.info( "copy actual file " + parts[i] + " to " + previouspartial );
        de.copyNode( 
                version,                       // version number of source to copy
                vs,                            // virtual server
                previouspartial,               // 
                parts[i], 
                de.getCreatedByPrincipalID(),  // same owner as source
                2,                             // weddav depth - 2 means infinite which is default depth
                false,                         // overwrite 
                DirectoryEntry.TRASH_OP.NONE,  // no trash operation
                false                          // not move, copy
        );
      }
    }
  }
  

  
  class NotificationRecipientInfo
  {
    String id;
  }
}
