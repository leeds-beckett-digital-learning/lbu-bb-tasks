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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.FileVersionInfo;
import uk.ac.leedsbeckett.bbtasks.xythos.BlobInfoMap;
import uk.ac.leedsbeckett.bbtasks.xythos.BuilderInfo;
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
    NumberFormat nf = NumberFormat.getIntegerInstance();
    SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd" );
    SimpleDateFormat japanformat = new SimpleDateFormat( "yyyyMMdd" );
    vs = VirtualServer.find( virtualservername );
    Path logfile = webappcore.logbase.resolve( "archivehugecoursefiles-analysis-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
    Path emailfile = webappcore.logbase.resolve( "archivehugecoursefiles-analysis-emails-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

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
      bimap = LocalXythosUtils.getArchivedHugeBinaryObjects( vs, null );
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

    try ( PrintWriter log = new PrintWriter( new FileWriter( emailfile.toFile() ) ); )
    {
      for ( BuilderInfo  builder : bimap.buildermap.values() )
      {
        log.println( "Email to " + builder.getName() + " " + builder.getFamilyname() + " <" + builder.getEmail() + ">" );
        log.println( "----------------------------" );
        for ( CourseInfo course : builder.getCourses() )
        {
          log.println( "    " + course.getTitle() );
          for ( LinkInfo link : course.getLinks() )
          {
            log.println( "        " + link.toString() );
          }
        }
        log.println();
        log.println();
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file - emails.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }    
}
