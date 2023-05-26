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
import java.util.Calendar;
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
  public static final String TARGET_BASE = "/institution/aa_technical/hugefiles";
  public static final String PLACEHOLDER = "/institution/aa_technical/hugefiles/placeholder.mp4";
  public static final long FILE_SIZE_THRESHOLD = (600L*1024L*1024L);
  
  public String virtualservername;
  public int accessedbeforeday;
  public int accessedbeforemonth;
  public int accessedbeforeyear;

  private transient VirtualServer vs;
  
  @JsonCreator
  public XythosArchiveHugeCourseFilesAnalysis( 
          @JsonProperty("virtualservername")   String virtualservername,
          @JsonProperty("accessedbeforeday")   int accessedbeforeday,
          @JsonProperty("accessedbeforemonth") int accessedbeforemonth,
          @JsonProperty("accessedbeforeyear")  int accessedbeforeyear
  )
  {
    this.virtualservername   = virtualservername;
    this.accessedbeforeday   = accessedbeforeday;
    this.accessedbeforemonth = accessedbeforemonth;
    this.accessedbeforeyear  = accessedbeforeyear;
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
    Date filterbylastaccessed = null;
    if ( accessedbeforeday > 0 && accessedbeforemonth > 0 && accessedbeforeyear > 0 )
    {
      Calendar c = Calendar.getInstance();
      c.set( accessedbeforeyear, accessedbeforemonth, accessedbeforeday, 0, 0, 0 );
      filterbylastaccessed = c.getTime();
    }
    
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to analyse huge course files. This may take many minutes." );
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
      bimap = LocalXythosUtils.getArchivedHugeBinaryObjects( debuglogger, vs, null );
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
      long totaloldbytes = 0L;
      long totalbytes = 0L;
      for ( BlobInfo bi : bimap.blobs )
      {
        if (Thread.interrupted())
        {
          log.println( "Task interrupted, output terminated." );
          break;
        }
        totalbytes += bi.getSize();
        log.print( "blobid = " + bi.getBlobId() + ", " + bi.getDigest() + ", " + nf.format(bi.getSize()/1000000) + "MiB,  run tot " );
        log.print( nf.format(totalbytes/1000000) + "MiB, " );
        log.print( bi.getLinkCount() + " links, " );
        if ( bi.getLastAccessed() != null )
          log.print( "Most Recent Module Access =              " + sdf.format(bi.getLastAccessed()) );
        log.print( ", " );
        log.println();
        
        if ( bi.getLinkCount() == 0 )
          totalunlinkedbytes += bi.getSize();
        if ( 
              bi.getLinkCount() > 0 && 
                ( 
                  bi.getLastAccessed() == null || 
                  (
                    filterbylastaccessed != null &&
                    bi.getLastAccessed().before( filterbylastaccessed )
                  )
                )
           )
          totaloldbytes += bi.getSize();
        
        for ( FileVersionInfo vi : bi.getFileVersions() )
        {
          log.println( "    File      " + vi.getPath() );
          if ( vi.getCoursePkId() == null )
            log.println( "    Not in a course." );
          else
            log.println( "    In Course " + vi.getCoursePkId().toExternalString() );
          for ( LinkInfo link :  bimap.linklistmap.get( vi.getStringFileId() ) )
          {
            CourseInfo ci = bimap.getCourseInfo( link.getLink().getCourseId() );
            log.print( "        Link " );
            log.print( link.getLink().getCourseId().toExternalString() + ", " );
            log.print( link.getLink().getLink().getId().toExternalString() + ", " );
            log.print( link.getLink().getParentEntity().getDataType().getName() + ", " );
            log.print( link.getContentId() + ", " );
            log.print( link.getContentFileId() + ", " );
            log.print( link.getPath() + ", " );
            log.println( (ci==null || ci.getLastAccessed() == null)?"":",              " + sdf.format(ci.getLastAccessed()) );
          }
        }
      }
      log.println();
      log.println();
      log.println( "Unlinked Blobs:" );
      for ( BlobInfo bi : bimap.blobs )
        if ( bi.getLinkCount() == 0 )
          log.println( bi.getBlobId() );
      
      if ( filterbylastaccessed != null )
      {
        log.println();
        log.println( "Older Linked Blobs:" );
        for ( BlobInfo bi : bimap.blobs )
          if ( bi.getLinkCount() > 0 &&
              (
                bi.getLastAccessed() == null 
                ||
                bi.getLastAccessed().before( filterbylastaccessed ) 
              ) )
            log.println( bi.getBlobId() );
      }
      log.println();
      log.println();
      log.println( "Total          " + nf.format( totalbytes ) );
      log.println( "Total unlinked " + nf.format( totalunlinkedbytes ) );
      if ( filterbylastaccessed != null )
      {
        log.println( "Total old      " + nf.format( totaloldbytes ) );
        log.println( "Total to arch. " + nf.format( totalunlinkedbytes + totaloldbytes ) );
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file.", ex );
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

        StringBuilder emailtext = new StringBuilder();
        int linkcount=0;
        
        emailtext.append( "----Start Email-----------------\n" );
        emailtext.append( ":ID:" );
        emailtext.append( emailcount++ );
        emailtext.append( "\n:Name:" );
        emailtext.append( builder.getName() );
        emailtext.append( "\n:Address:" );
        emailtext.append( builder.getEmail() );
        emailtext.append( "\n:Subject:Video Housekeeping in My Beckett\n" );
        for ( CourseInfo course : builder.getCourses() )
        {
          if ( !course.getLinks().isEmpty() && 
                  ( filterbylastaccessed==null             || 
                    course.getLastAccessed()==null || 
                    course.getLastAccessed().before( filterbylastaccessed ) ) )
          {
            emailtext.append( "<h3>" );
            emailtext.append( course.getTitle() );
            emailtext.append( "</h3>\n<ol>\n" );
            for ( LinkInfo link : course.getLinks() )
            {
              emailtext.append( "<li>" );
              emailtext.append( link.getPath().replace( ">", "<strong>---&gt;</strong>" ) );
              emailtext.append( "</li>\n" );
              linkcount++;
            }
            emailtext.append( "</ol>\n" );
          }
        }
        emailtext.append( "----End Email-----------------\n\n\n" );
        
        if ( linkcount > 0 )
          log.println( emailtext.toString() );
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error writing to task output file - emails.", ex );
    }
    
    debuglogger.info( "Task is complete." );
    
  }    
}
