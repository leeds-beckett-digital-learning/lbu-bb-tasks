/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xythos.common.api.VirtualServer;
import com.xythos.fileSystem.Directory;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import uk.ac.leedsbeckett.bbtasks.BlobSearchResult;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 *
 * @author jon
 */
public class XythosAnalyseAutoArchiveTask extends BaseTask
{
  public String vsname;
  public int y, m, d, y2, m2, d2;
  
  transient int datecode, datecode2;

  @JsonCreator
  public XythosAnalyseAutoArchiveTask(
          @JsonProperty("vsname") String vsname, 
          @JsonProperty("y") int y, 
          @JsonProperty("m") int m, 
          @JsonProperty("d") int d, 
          @JsonProperty("y2") int y2, 
          @JsonProperty("m2") int m2, 
          @JsonProperty("d2") int d2 )
  {
    this.vsname = vsname;
    this.y = y;
    this.m = m;
    this.d = d;
    this.y2 = y2;
    this.m2 = m2;
    this.d2 = d2;
    datecode  =  y*10000 +  m*100 + d;
    datecode2 = y2*10000 + m2*100 + d2;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    Calendar calends = Calendar.getInstance( TimeZone.getTimeZone( "GMT" ) );
    VirtualServer vs = VirtualServer.find(vsname );
    ArrayList<BlobSearchResult> list = new ArrayList<>();
    Path logfile = webappcore.logbase.resolve( "autoarchiveanalysis-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
    long tiirunningtotal = 0L;
    long csrunningtotal = 0L;

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to analyse autoarchives. This may take many minutes." );
    }
    catch (IOException ex)
    {
      webappcore.logger.error( "Error writing to task output file.", ex );
      return;
    }

    webappcore.logger.info( "Analyse autoarchives process started. May take many minutes. " + datecode + " to " + datecode2 ); 
    long start = System.currentTimeMillis();

    Context context = null;
    FileSystemEntry entry = null;
    StringBuilder message = new StringBuilder();
    try
    {
      context = AdminUtil.getContextForAdmin("MultipleAppServerProgram:150");
      entry = FileSystem.findEntry( vs, "/internal/autoArchive", false, context );
      if ( entry == null )
      {
        message.append( "Could not find entry.\n" );
      }
      else
      {
        //message.append( "Entry " + entry.getName() + " java class = " + entry.getClass().getCanonicalName() + "\n" );
        if ( entry instanceof Directory )
        {
          Directory dir = (Directory)entry;
          FileSystemEntry[] entries = dir.getDirectoryContents(true);
          for ( int i=0; i < entries.length; i++ )
          {
            if ( Thread.interrupted() ) throw new InterruptedException();
            FileSystemEntry f = entries[i];
            if ( !(f instanceof com.xythos.fileSystem.File) )
              continue;
            boolean iszip = "application/zip".equals( f.getFileContentType() );
            LocalDateTime dt = f.getCreationTimestamp().toLocalDateTime();
            LocalDateTime dtu = f.getLastUpdateTimestamp().toLocalDateTime();
            int fdatecode = dt.getYear() * 10000 + dt.getMonthValue()*100 + dt.getDayOfMonth();
            long fsecondsa = dt.getYear() * 10000000000L + dt.getMonthValue()*100000000L + dt.getDayOfMonth()*1000000L + dt.getHour()*10000L + dt.getMinute()*100L + dt.getSecond();
            long fsecondsb = dtu.getYear() * 10000000000L + dtu.getMonthValue()*100000000L + dtu.getDayOfMonth()*1000000L + dtu.getHour()*10000L + dtu.getMinute()*100L + dtu.getSecond();
            webappcore.logger.info( ",\"" + f.getName() + "\"," + f.getFileContentType() + "," + fdatecode + "," + f.getEntrySize() + "," + fsecondsa + "," + fsecondsb + "," + (fsecondsb-fsecondsa) ); 
            if ( !iszip )
              continue;

            if ( fdatecode >= datecode && fdatecode <= datecode2 )
            {
              //bbmonitor.debuglogger.info( "Working on entry " + i + " of " + entries.length );
              BlobSearchResult bsr = new BlobSearchResult();
              list.add(bsr);
              LocalXythosUtils.analyseZip( (com.xythos.fileSystem.File)f, bsr, false );
              tiirunningtotal += bsr.turnitinusage;
              csrunningtotal += bsr.csfilesusage;
            }
          }
        }
      }
    }
    catch ( Throwable th )
    {
      webappcore.logger.error( "Error occured while running analysis of autoarchives.", th);
    }

    int n=0;
    for ( BlobSearchResult i : list )
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      if ( i.analysed )
      {
        n++;
        message.append( "blob," + i.id + "," + i.refcount + "," + i.size + "," + i.storagefilename + "," + i.tempstoragefilename + "," + (i.iszip?"zip":"other") + "," + i.totalusage + "," + i.turnitinusage + "," + i.csfilesusage + "," );
        if ( i.created == null )
          message.append( "unknowndate" );
        else
        {
          LocalDateTime dt = i.created.toLocalDateTime();
          message.append( dt.getYear() );
          message.append( "/" );
          message.append( dt.getMonthValue() );
          message.append( "/" );
          message.append( dt.getDayOfMonth() );
        }
//            if ( i.signature != null )
//              for ( byte x : i.signature )
//                medialog.append( " " + Integer.toHexString( (0xff & x) | 0x100 ).substring(1) );
        message.append( "\n" );
      }
    }
    message.append( "Blob count = " + list.size() + " analysed zips = " + n + "\n" );
    message.append( "Found " + tiirunningtotal + " bytes of Turnitin uploads in the autoarchives." );
    message.append( "Found " + csrunningtotal + " bytes of 'legacy' content uploads in the autoarchives." );

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
