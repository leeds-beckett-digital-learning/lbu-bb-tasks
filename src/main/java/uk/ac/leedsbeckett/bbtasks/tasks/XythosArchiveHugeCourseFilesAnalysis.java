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
import blackboard.platform.contentsystem.manager.ResourceLinkManager;
import blackboard.platform.contentsystem.service.ContentSystemServiceExFactory;
import blackboard.platform.course.CourseEnrollmentManager;
import blackboard.platform.course.CourseEnrollmentManagerFactory;
import blackboard.platform.gradebook2.CourseUserInformation;
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
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.FileVersionInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfoMap;
import uk.ac.leedsbeckett.bbtasks.xythos.CourseInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LinkInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 *
 * @author jon
 */
public class XythosArchiveHugeCourseFilesAnalysis extends BaseTask
{
  public static final String TARGET_BASE = "/institution/hugefiles";
  public static final String PLACEHOLDER = "/institution/hugefiles/placeholder.mp4";
  public static final long FILE_SIZE_THRESHOLD = (600L*1024L*1024L);
  
  public String virtualservername;

  private transient VirtualServer vs;
  
  @JsonCreator
  public XythosArchiveHugeCourseFilesAnalysis( 
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

    debuglogger.info( "Starting to analyse archived huge course files. " ); 
    debuglogger.info( "Virtual server " + virtualservername ); 
    long start = System.currentTimeMillis();
    BlobInfoMap bimap=null;
    long unlinkedbytes=0L;
    try
    {
      bimap = LocalXythosUtils.getArchivedHugeBinaryObjects( vs );


      ResourceLinkManager rlm = ContentSystemServiceExFactory.getInstance().getResourceLinkManager();
      CourseEnrollmentManager cemanager = CourseEnrollmentManagerFactory.getInstance();
      
      List<CSResourceLinkWrapper> rawlinks = null;
      List<LinkInfo> links = null;
      for ( FileVersionInfo bi : bimap.allversions )
      {
        rawlinks = rlm.getResourceLinks( Long.toString( bi.getFileId() ) + "_1" );
        links = new ArrayList<>();
        for ( CSResourceLinkWrapper rawlink : rawlinks )
        {
          Date latestdate=null;
          CourseInfo ci = bimap.getCourseInfo( rawlink.getCourseId() );
          if ( ci == null )
          {
            List<CourseUserInformation> studentlist = cemanager.getStudentByCourseAndGrader( rawlink.getCourseId(), null );
            if ( studentlist != null )
            {
              for ( CourseUserInformation student : studentlist )
              {
                Date d = student.getLastAccessDate();
                if ( d != null && (latestdate == null || d.after( latestdate )) )
                  latestdate = d;
              }
            }
            ci = new CourseInfo( rawlink.getCourseId(), latestdate );
            bimap.addCourseInfo( ci );
          }
          links.add( new LinkInfo( rawlink, ci.getLastAccessed() ) );
        }
        bimap.addLinks( bi.getStringFileId(), links );
      }      

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
        log.println( bi.getBlobId() + ", " + bi.getSize() + ", " + bi.getLinkCount() );
        totalbytes += bi.getSize();
        if ( bi.getLinkCount() == 0 )
          totalunlinkedbytes += bi.getSize();
        for ( FileVersionInfo vi : bi.getFileVersions() )
        {
          log.println( "    " + vi.getPath() + ", " + vi.getFileId() );
          for ( LinkInfo link :  bimap.linklistmap.get( vi.getStringFileId() ) )
          {
            CourseInfo ci = bimap.getCourseInfo( link.getLink().getCourseId() );
            log.print( "        LINK: " + link.getLink().getCourseId().toExternalString() + ", " + link.getLink().getParentDisplayName() );
            log.println( (ci==null || ci.getLastAccessed() == null)?"":", " + ci.getLastAccessed().toGMTString() );
          }
        }
      }
      log.println();
      log.println( "Total          " + totalbytes );
      log.println( "Total unlinked " + totalunlinkedbytes );
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }    
}
