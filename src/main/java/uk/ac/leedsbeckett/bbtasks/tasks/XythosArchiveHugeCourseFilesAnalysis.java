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
    ArrayList<BlobInfo> list=null;
    try
    {
      list = LocalXythosUtils.getArchivedHugeBinaryObjects( vs );
      debuglogger.info( "Done." );
    }
    catch ( Exception ex )
    {
      debuglogger.error( "Unable to complete analysis.", ex );
    }


    ResourceLinkManager rlm = ContentSystemServiceExFactory.getInstance().getResourceLinkManager();
    List<CSResourceLinkWrapper> links = null;
    int linkcount = 0;
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      for ( BlobInfo bi : list )
      {
        links = rlm.getResourceLinks( Long.toString( bi.getFileId() ) + "_1" );
        linkcount = ( links == null ) ? 0 : links.size();
        log.println( bi.getBlobId() + ", " + bi.getPath() + ", " + bi.getSize() + ", " + bi.getFileId() + ", " + linkcount );
        if ( linkcount > 0 )
          log.println( "----- HAS LINKS -----" );
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }    
}
