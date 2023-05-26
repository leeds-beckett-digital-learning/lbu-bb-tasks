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
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipSearch;
import blackboard.persist.user.UserDbLoader;
import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import blackboard.platform.contentsystem.service.ContentSystemServiceExFactory;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.Revision;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import uk.ac.leedsbeckett.bbtasks.TaskException;

/**
 *
 * @author jon
 */
public class XythosArchiveHugeCourseFilesStageTwoTask extends BaseTask
{
  public static final String TARGET_BASE = "/institution/aa_technical/hugefiles";
  public static final String PLACEHOLDER = "/institution/aa_technical/hugefiles/placeholder.mp4";
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
    
    ArrayList<CourseInfo> courselist = null;
    Collection<NotificationRecipientInfo> recipients = null;

    try
    {
      courselist = getListOfArchiveFolders();
      debuglogger.info( "Done with list of archive folders." );
      recipients = getListOfNotificationRecipients( courselist );
      debuglogger.info( "Done with recipient list." );
    }
    catch ( Exception ex )
    {
      debuglogger.error( "Unable to list email recipients.", ex );
    }

    debuglogger.error( "Number of email recipients  = " + recipients.size() );
    debuglogger.error( "Number of courses (modules) = " + courselist.size() );
      
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      for ( NotificationRecipientInfo n : recipients )
      {
        log.println( n.getId() + ", " + n.getName() + ", "+ n.getEmail() );
        for ( CourseInfo ci : n.getCourses() )
        {
          log.println( "    " + ci.getCourseId() + ", " + ci.getTitle() );            
          for ( FileInfo fi : ci.getFiles() )
          {
            log.print( "        " );
            if ( fi.getLinkcount() == 0 )
              log.print( "NO LINKS, " );
            else
              log.print( "HAS LINKS " + fi.getLinkcount() + ", " );
            log.print( fi.getBlobid() + ", " );
            log.println( (fi.getSize()/1000000L) + "MiB, " + 
                         fi.getName().substring( ci.getName().length() ) );
          }
        }
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }  

  
  ArrayList<CourseInfo> getListOfArchiveFolders() throws InterruptedException, TaskException
  {
    Context context=null;
    FileSystemEntry baseentry;
    ArrayList<CourseInfo> courselist = new ArrayList<>();

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
        CourseInfo info = new CourseInfo( d.getName(), d.getCreatedByPrincipalID() );
        courselist.add( info );
        getCourseFiles( context, d, info );
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

  void getCourseFiles( Context context, FileSystemDirectory d, CourseInfo info ) throws XythosException, TaskException
  {
    FileSystemEntry[] entries = d.getDirectoryContentsRecursive( false, Integer.MAX_VALUE );
    for ( FileSystemEntry entry : entries )
      if ( entry.getFileContentType() != null && entry.getFileContentType().startsWith( "video/" ) )
      {
        if ( !(entry instanceof com.xythos.fileSystem.File) )
          throw new TaskException( "Unexpected class of object for file system entry. " + entry.getClass() );
        com.xythos.fileSystem.File file = (com.xythos.fileSystem.File)entry;
        Revision revision = file.getRevision( file.getLatestFileVersion() );
        long blobid = revision.getBlobID();
        // these entries are in the archive so it is pointless to look for
        // links to them!
//        List<CSResourceLinkWrapper> links = 
//                ContentSystemServiceExFactory.getInstance().getResourceLinkManager()
//                        .getResourceLinks( entry.getName() );
        FileInfo fileinfo = new FileInfo( entry.getName(), entry.getEntrySize(), blobid, 0 );
        info.addFile( fileinfo );
      }
  }
  
  
  Collection<NotificationRecipientInfo> getListOfNotificationRecipients( List<CourseInfo> sourcelist ) throws PersistenceException, TaskException
  {
    // Find instructor group for each module
    // Make a big list of instructors without duplicates
    HashMap<String,NotificationRecipientInfo> useridmap = new HashMap<>();
    
    for ( CourseInfo source : sourcelist )
    {
      //debuglogger.info( "Looking for members of [" + source.getCourseId() + "]" );
      Course course = null;
      try
      {
        course = CourseDbLoader.Default.getInstance().loadByCourseId( source.getCourseId() );
      }
      catch ( KeyNotFoundException knfex )
      {
        debuglogger.info( "Cannot find a course with ID [" + source.getCourseId() + "]" );
        continue;
      }
      UserDbLoader userloader = UserDbLoader.Default.getInstance();
      if ( course == null )
        throw new TaskException( "Missing course for course id " + source.getCourseId() );
      //debuglogger.info( "Database id of course [" + course.getCourseId() + "]" );
  
      source.setTitle( course.getTitle() );
      
      Role[] roles = { Role.INSTRUCTOR, Role.COURSE_BUILDER };
      for ( Role role : roles  )
      {
        CourseMembershipSearch query = new CourseMembershipSearch( course.getId() );
        query.searchByRole( role.getIdentifier() );
        query.run();
        List list = query.getResults();
        for ( Object o : list )
        {
          NotificationRecipientInfo nri;
          if ( o instanceof CourseMembership )
          {
            CourseMembership cm = (CourseMembership)o;
            //debuglogger.info( cm.getCourseId().toExternalString() + " " + cm.getUserId().toExternalString() );
            nri = useridmap.get( cm.getUserId().toExternalString() );
            if ( nri == null )
            {
              try
              {
                User user = userloader.loadById( cm.getUserId() );
                nri = new NotificationRecipientInfo( 
                                cm.getUserId().toExternalString(), 
                                user.getFamilyName(),
                                user.getGivenName() + " " + user.getFamilyName(),
                                user.getEmailAddress()
                        );
                useridmap.put( nri.getId(), nri );
              }
              catch ( KeyNotFoundException knfex )
              {
                nri = new NotificationRecipientInfo( cm.getUserId().toExternalString(), null, null, null );              
              }
            }
            nri.addCourseInfo( source );
          }
          else
            debuglogger.error( "Returned object not of type CourseMembership" );
        }
      }
    }
    
    ArrayList<NotificationRecipientInfo> list = new ArrayList<>( useridmap.values() );
    list.sort(( NotificationRecipientInfo o1, NotificationRecipientInfo o2 ) -> o1.getFamilyname().compareTo( o2.getFamilyname() ));
    return list;
  }

  class CourseInfo
  {
    final String name;
    final String principal;
    final String courseid;
    final String courseinstructorrole;
    String title;
    final ArrayList<FileInfo> files = new ArrayList<>();

    public CourseInfo( String name, String principal )
    {
      this.name = name;
      this.principal = principal;
      String[] list = name.split( "/" );
      courseid = list[ list.length - 1 ];
      courseinstructorrole = "G:CR:" + courseid + ":INSTRUCTOR";
    }

    public String getName()
    {
      return name;
    }

    public String getCourseId()
    {
      return courseid;
    }

    public String getCourseInstructorRole()
    {
      return courseinstructorrole;
    }
    
    public List<FileInfo> getFiles()
    {
      return files;
    }

    public String getTitle()
    {
      return title;
    }

    public void setTitle( String title )
    {
      this.title = title;
    }

    
    public void addFile( FileInfo file )
    {
      files.add( file );
    }
  }

  public class FileInfo
  {
    final String name;
    final long size;
    final long blobid;
    final int linkcount;

    public FileInfo( String name, long size, long blobid, int linkcount )
    {
      this.name      = name;
      this.size      = size;
      this.blobid    = blobid;
      this.linkcount = linkcount;
    }

    public String getName()
    {
      return name;
    }

    public long getSize()
    {
      return size;
    }

    public long getBlobid()
    {
      return blobid;
    }

    public int getLinkcount()
    {
      return linkcount;
    }
  }

  
  
  class NotificationRecipientInfo
  {
    final String id;
    final String familyname;
    final String name;
    final String email;
    final ArrayList<CourseInfo> courses = new ArrayList<>();

    public NotificationRecipientInfo( String id, String familyname, String name, String email )
    {
      this.id = id;
      this.familyname = familyname;
      this.name = name;
      this.email = email;
    }

    public String getId()
    {
      return id;
    }

    public String getFamilyname()
    {
      return familyname;
    }

    
    public String getName()
    {
      return name;
    }

    
    public String getEmail()
    {
      return email;
    }

    public List<CourseInfo> getCourses()
    {
      return courses;
    }

    
    
    public void addCourseInfo( CourseInfo ci )
    {
      courses.add( ci );
    }
  }
}
