/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.File;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.CreateDirectoryData;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.permissions.api.AccessControlEntry;
import com.xythos.storageServer.permissions.api.DirectoryAccessControlEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.leedsbeckett.bbtasks.BlobSearchResult;

/**
 *
 * @author jon
 */
public class XythosMoveHugeCourseFilesTask extends BaseTask
{
  public static final String TARGET_BASE = "/institution/bigfiles/courses";
  public static final String PLACEHOLDER = "/institution/bigfiles/placeholder.mp4";
  
  public String virtualservername;
  public String coursecoderegex;

  @JsonCreator
  public XythosMoveHugeCourseFilesTask( 
          @JsonProperty("virtualservername") String virtualservername, 
          @JsonProperty("coursecoderegex")   String coursecoderegex )
  {
    this.virtualservername = virtualservername;
    this.coursecoderegex = coursecoderegex;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    VirtualServer vs = VirtualServer.find( virtualservername );
    Path logfile = webappcore.logbase.resolve( "movehugecoursefiles-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to move huge course files. This may take many minutes." );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
      return;
    }

    debuglogger.info( "Starting to move huge course files. May take many minutes. " ); 
    debuglogger.info( "Virtual server " + virtualservername ); 
    long start = System.currentTimeMillis();

    
    
    
    ArrayList<FileSystemDirectory> courselist = new ArrayList<>();
    
    Context context = null;
    FileSystemEntry entry = null;
    StringBuilder message = new StringBuilder();
    FileSystemDirectory targetdir = null;
    int placeholderlatestversion=0;
    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        return;
      }

      FileSystemEntry placeholderentry = FileSystem.findEntry( vs, PLACEHOLDER, false, context );
      if ( placeholderentry == null || !(placeholderentry instanceof File) )
      {
        debuglogger.error( "Could not find the placeholder video file. Stopping\n" );
        return;
      }
      placeholderlatestversion = ((File)placeholderentry).getLatestFileVersion();
      
      entry = FileSystem.findEntry( vs, TARGET_BASE, false, context );
      if ( entry == null || !(entry instanceof FileSystemDirectory) )
      {
        debuglogger.error( "Could not find /institution/bigfiles/courses directory.\n" );
        return;
      }
      targetdir = (FileSystemDirectory)entry;
      
      entry = FileSystem.findEntry( vs, "/courses", false, context );
      if ( entry == null || !(entry instanceof FileSystemDirectory) )
      {
        debuglogger.error( "Could not find /courses directory.\n" );
        return;
      }
      FileSystemDirectory sourcedir = (FileSystemDirectory)entry;
      
      
      
      FileSystemEntry[] entries = sourcedir.getDirectoryContentsNonRecursive( false );
      for ( FileSystemEntry subentry : entries )
      {
        if ( Thread.interrupted() ) throw new InterruptedException();
        if ( !( subentry instanceof com.xythos.fileSystem.Directory ) ) continue;
        FileSystemDirectory d = (FileSystemDirectory) subentry;
        if ( d.getName().matches( coursecoderegex ) )
          courselist.add( d );        
      }
    }
    catch ( Throwable th )
    {
      debuglogger.error( "Error occured while running analysis of autoarchives.", th);
    }

    for ( FileSystemDirectory d : courselist )
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      String dname = d.getName().substring( d.getName().lastIndexOf( '/' )+1 );
      message.append( "Course directory: " );
      message.append( dname );
      message.append( "\n" );
      
      String targetname = TARGET_BASE + "/" + dname;
      try
      {
        ArrayList<FileSystemEntry> coursefilelist = new ArrayList<>();
        
        FileSystemEntry[] coursefiles = d.getDirectoryContentsRecursive( false, 10000 );
        for ( FileSystemEntry coursefile : coursefiles )
        {
          if (   coursefile != null &&
                 coursefile.getFileContentType() != null &&
                 coursefile.getFileContentType().startsWith( "video/" ) &&
                 coursefile.getTotalSize() > (10L*1024L*1024L) )
            coursefilelist.add( coursefile );
        }
        
        if ( coursefilelist.isEmpty() )
        {
          message.append( "    No file to move in this course.\n" );
          continue;
        }

        // only create target directory if there are file that need to be moved.
        FileSystemDirectory fsd;
        entry = FileSystem.findEntry( vs, targetname, false, context );
        if ( entry != null && !(entry instanceof FileSystemDirectory) )
        {
          debuglogger.error( "Non-directory entry is blocking the creation of this directory." );
          continue;
        }
        if ( entry == null )
        {
          CreateDirectoryData cdc = new CreateDirectoryData( vs, TARGET_BASE, dname, d.getEntryOwnerPrincipalID() );
          fsd = FileSystem.createDirectory( cdc, context );
          debuglogger.info( "Created (but not committed)." + fsd.getName() );
        }
        else
        {
          fsd = (FileSystemDirectory)entry;
        }
        debuglogger.info( "Target directory now exists:" + fsd.getName() );
        String courseinstructorrole = "G:CR:" + dname + ":INSTRUCTOR";
        DirectoryAccessControlEntry courseace = (DirectoryAccessControlEntry)fsd.getAccessControlEntry( courseinstructorrole );
        if ( courseace == null )
        {
          debuglogger.error( "Couldn't obtain DirectoryAccessControlEntry for " + fsd.getName() );
          continue;
        }
        courseace.setReadable( true );
        courseace.setChildInheritReadable( true );
        fsd.recursivePermissionsOverwrite();
                
        for ( FileSystemEntry coursefile : coursefilelist )
        {
          FileSystemEntry placeholderentry = FileSystem.findEntry( vs, PLACEHOLDER, false, context );
          String fullpath = coursefile.getName();
          String sourceparent = fullpath.substring( 0, fullpath.lastIndexOf( '/' ) );
          String filename = fullpath.substring( fullpath.lastIndexOf( '/' ) + 1 );
          String targetparent = TARGET_BASE + fullpath.substring( "/courses".length() );
          targetparent = targetparent.substring( 0, targetparent.lastIndexOf( '/' ) );
          message.append( "      Move " );
          message.append( coursefile.getName() );
          message.append( " to " );
          message.append( filename );
          message.append( " in " );
          message.append( targetparent );
          message.append(  "\n" );
          coursefile.move( targetparent, filename, false );
          placeholderentry.copyToLocal( placeholderlatestversion, vs, sourceparent, filename, coursefile.getEntryOwnerPrincipalID(), false );
        }        

        context.commitContext();
      }
      catch ( Exception ex )
      {
        debuglogger.error( "Exception ", ex );
        message.append( "XythosException while attempting to process directory. Ending task.\n" );
        break;
      }
    }

    if ( context != null && context.isDirty() )
    {
      try
      {
        context.rollbackContext();
      }
      catch ( XythosException ex1 )
      {
        debuglogger.error( "Unable to roll back context.", ex1 );
      }        
    }
    
    long end = System.currentTimeMillis();
    float elapsed = 0.001f * (float)(end-start);
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( message );
      log.println( "Analyse autoarchives process ended after " + elapsed + " seconds. "      );
    }
    catch ( IOException ex )
    {
      webappcore.logger.error( "Error writing to output file.", ex);
    }
  }  
}
