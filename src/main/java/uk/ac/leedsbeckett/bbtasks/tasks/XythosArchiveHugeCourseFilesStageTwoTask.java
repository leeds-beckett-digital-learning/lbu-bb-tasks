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

import blackboard.data.course.Course;
import blackboard.data.course.CourseMembership;
import blackboard.data.course.CourseMembership.Role;
import blackboard.data.user.User;
import blackboard.persist.DataType;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipSearch;
import blackboard.persist.course.CourseSearch;
import blackboard.persist.user.UserDbLoader;
import blackboard.util.SearchUtil;
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
import com.xythos.storageServer.permissions.api.DirectoryAccessControlEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.leedsbeckett.bbtasks.TaskException;

/**
 *
 * @author jon
 */
public class XythosArchiveHugeCourseFilesStageTwoTask extends BaseTask
{
  public static final String TARGET_BASE = "/institution/hugefiles";
  public static final String PLACEHOLDER = "/institution/hugefiles/placeholder.mp4";
  public static final long FILE_SIZE_THRESHOLD = (600L*1024L*1024L);
  
  public String virtualservername;

  private transient VirtualServer vs;
  
  @JsonCreator
  public XythosArchiveHugeCourseFilesStageTwoTask( 
          @JsonProperty("virtualservername") String virtualservername )
  {
    this.virtualservername = virtualservername;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    vs = VirtualServer.find( virtualservername );
    Path logfile = webappcore.logbase.resolve( "archivehugecoursefiles-stage2" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

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
    
    ArrayList<XythosDirectoryCreationInfo> courselist = null;
    Collection<NotificationRecipientInfo> recipients = null;

    courselist = getListOfArchiveFolders();
    debuglogger.info( "Done with list of archive folders." );
    try
    {
      recipients = getListOfNotificationRecipients( courselist );
      debuglogger.info( "Done with recipient list." );
    }
    catch ( Exception ex )
    {
      debuglogger.error( "Unable to list email recipients.", ex );
    }

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      if ( recipients != null )
        for ( NotificationRecipientInfo n : recipients )
          log.println( n.getId() + ", " + n.getName() + ", "+ n.getEmail() );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }  

  
  ArrayList<XythosDirectoryCreationInfo> getListOfArchiveFolders() throws InterruptedException
  {
    Context context=null;
    FileSystemEntry baseentry;
    ArrayList<XythosDirectoryCreationInfo> courselist = new ArrayList<>();

    try
    {
      context = AdminUtil.getContextForAdmin( "XythosMoveHugeCourseFilesTask" );
      if ( context == null )
      {
        debuglogger.error( "Unable to obtain Xythos context for admin.\n" );
        return null;
      }
      
      baseentry = FileSystem.findEntry( vs, TARGET_BASE, false, context );
      if ( baseentry == null || !(baseentry instanceof FileSystemDirectory) )
      {
        debuglogger.error( "Could not find base directory directory.\n" );
        return null;
      }
      FileSystemDirectory basedir = (FileSystemDirectory)baseentry;

      FileSystemEntry[] entries = basedir.getDirectoryContentsNonRecursive( false );
      for ( FileSystemEntry subentry : entries )
      {
        // yield if someone is trying to interrupt this task
        if ( Thread.interrupted() ) throw new InterruptedException();
        // skip if not a directory
        if ( !( subentry instanceof com.xythos.fileSystem.Directory ) ) continue;
        FileSystemDirectory d = (FileSystemDirectory) subentry;
        courselist.add( new XythosDirectoryCreationInfo( d.getName(), d.getCreatedByPrincipalID() ) );
      }      
    }
    catch ( XythosException th )
    {
      debuglogger.error( "Error occured listing course module directories.", th);
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

    return courselist;
  }
  
  
  
  Collection<NotificationRecipientInfo> getListOfNotificationRecipients( List<XythosDirectoryCreationInfo> sourcelist ) throws PersistenceException, TaskException
  {
    // Find instructor group for each module
    // Make a big list of instructors without duplicates
    HashMap<String,NotificationRecipientInfo> useridmap = new HashMap<>();
    
    for ( XythosDirectoryCreationInfo source : sourcelist )
    {
      debuglogger.info( "Looking for members of [" + source.getCourseId() + "]" );
      Course course = CourseDbLoader.Default.getInstance().loadByCourseId( source.getCourseId() );
      UserDbLoader userloader = UserDbLoader.Default.getInstance();
      if ( course == null )
        throw new TaskException( "Missing course for course id " + source.getCourseId() );
      debuglogger.info( "Database id of course [" + course.getId() + "]" );
      
      CourseMembershipSearch query = new CourseMembershipSearch( course.getId() );
      query.searchByRole( Role.INSTRUCTOR.getIdentifier() );
      query.run();
      List list = query.getResults();
      for ( Object o : list )
      {
        if ( o instanceof CourseMembership )
        {
          CourseMembership cm = (CourseMembership)o;
          debuglogger.info( cm.getCourseId().toExternalString() + " " + cm.getUserId().toExternalString() );
          if ( useridmap.containsKey( cm.getUserId().toExternalString() ) )
          {
            debuglogger.info( "Already found." );
          }
          else
          {
            debuglogger.info( "Added." );
            try
            {
              User user = userloader.loadById( cm.getUserId() );
              NotificationRecipientInfo nri = 
                      new NotificationRecipientInfo( 
                              cm.getUserId().toExternalString(), 
                              user.getGivenName() + " " + user.getFamilyName(),
                              user.getEmailAddress()
                      );
              useridmap.put( nri.getId(), nri );
            }
            catch ( KeyNotFoundException knfex )
            {
              NotificationRecipientInfo nri = 
                      new NotificationRecipientInfo( 
                              cm.getUserId().toExternalString(), 
                              null,
                              null
                      );              
            }
          }
            
        }
        else
          debuglogger.error( "Returned object not of type CourseMembership" );
      }
    }

    return useridmap.values();
  }

  class NotificationRecipientInfo
  {
    final String id;
    final String name;
    final String email;

    public NotificationRecipientInfo( String id, String name, String email )
    {
      this.id = id;
      this.name = name;
      this.email = email;
    }

    public String getId()
    {
      return id;
    }

    public String getName()
    {
      return name;
    }

    
    public String getEmail()
    {
      return email;
    }
    
    
  }
}
