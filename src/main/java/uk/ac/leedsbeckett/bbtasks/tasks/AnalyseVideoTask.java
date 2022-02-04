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

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xythos.common.InternalException;
import com.xythos.common.api.XythosException;
import com.xythos.common.properties.UnsupportedPropertyException;
import com.xythos.fileSystem.DirectoryEntry;
import com.xythos.fileSystem.Revision;
import com.xythos.fileSystem.util.BlobCreator;
import com.xythos.fileSystem.util.BlobCreatorFromInputStream;
import com.xythos.security.ContextImpl;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemUtil;
import com.xythos.storageServer.api.InvalidRequestException;
import com.xythos.storageServer.api.StorageServerException;
import com.xythos.storageServer.properties.api.DuplicatePropertyException;
import com.xythos.storageServer.properties.api.InvalidPropertyValueException;
import com.xythos.storageServer.properties.api.LongProperty;
import com.xythos.storageServer.properties.api.Property;
import com.xythos.storageServer.properties.api.PropertyDefinition;
import com.xythos.storageServer.properties.api.PropertyDefinitionManager;
import com.xythos.storageServer.properties.api.PropertyException;
import com.xythos.storageServer.properties.api.PropertyValueFormatException;
import com.xythos.storageServer.properties.api.PropertyWriteException;
import com.xythos.webdav.dasl.api.DaslResultSet;
import com.xythos.webdav.dasl.api.DaslStatement;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import uk.ac.leedsbeckett.bbtasks.VideoSearchResult;
import uk.ac.leedsbeckett.bbtasks.XythosAdapterChannel;

/**
 *
 * @author jon
 */
public class AnalyseVideoTask extends BaseTask
{

  public static final String CUSTOM_PROPERTIES_NAMESPACE   = "my.leedsbeckett.ac.uk/mediaanalysis";
  
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_ANALYSEDETAG = 
          new LocalPropertyDefinition( 
                  "analysedetag", 
                  PropertyDefinition.DATATYPE_SHORT_STRING, 
                  "The etag value when the media metadata was last analysed.",
                  false );

  public static final LocalPropertyDefinition CUSTOM_PROPERTY_MEDIADATARATE = 
          new LocalPropertyDefinition( 
                  "mediadatarate", 
                  PropertyDefinition.DATATYPE_LONG, 
                  "The data rate, in bytes per second of the medium.",
                  false );

  public static final LocalPropertyDefinition CUSTOM_PROPERTY_MEDIADURATION = 
          new LocalPropertyDefinition( 
                  "mediaduration", 
                  PropertyDefinition.DATATYPE_LONG, 
                  "The duration of the content in seconds.",
                  false );
          
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_MIMETYPE = 
          new LocalPropertyDefinition( 
                  "mimetype", 
                  PropertyDefinition.DATATYPE_SHORT_STRING, 
                  "The mimetype of the content as determined by scanning the file.",
                  false );
          
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_MEDIALOG = 
          new LocalPropertyDefinition( 
                  "medialog", 
                  PropertyDefinition.DATATYPE_STRING, 
                  "A full text description of all the content's metadata.",
                  false );
          
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_RECOMPRESSION = 
          new LocalPropertyDefinition( 
                  "recompression", 
                  PropertyDefinition.DATATYPE_LONG, 
                  "The recompression quality factor that was used or -1 is the file hasn't been recompressed.",
                  true );
          
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_ORIGINALDIGEST = 
          new LocalPropertyDefinition( 
                  "original-digest", 
                  PropertyDefinition.DATATYPE_SHORT_STRING, 
                  "The digest of the original file that was recompressed.",
                  true );
          
  public static final LocalPropertyDefinition CUSTOM_PROPERTY_ORIGINALSIZE = 
          new LocalPropertyDefinition( 
                  "original-size", 
                  PropertyDefinition.DATATYPE_LONG, 
                  "The original size of the file that was recompressed.",
                  true );
          
  public static final LocalPropertyDefinition[] localpropdefs =
  {
    CUSTOM_PROPERTY_ANALYSEDETAG,
    CUSTOM_PROPERTY_MIMETYPE,
    CUSTOM_PROPERTY_MEDIADATARATE,
    CUSTOM_PROPERTY_MEDIADURATION,
    CUSTOM_PROPERTY_MEDIALOG,
    CUSTOM_PROPERTY_RECOMPRESSION,
    CUSTOM_PROPERTY_ORIGINALDIGEST,
    CUSTOM_PROPERTY_ORIGINALSIZE
  };

  
  
  public static final long THRESHOLD_RATE = 100L * 1000L;  // mean bytes per second
  
  public static final String VIDEO_SEARCH_DASL
          = "<?xml version=\"1.0\" ?>"
          + "  <d:searchrequest xmlns:d=\"DAV:\" xmlns:m=\"" + CUSTOM_PROPERTIES_NAMESPACE + "\">"
          + "  <d:basicsearch>"
          + "    <d:select>"
          + "      <d:allprop/>"
          + "    </d:select>"
          + "    <d:from>"
          + "      <d:scope>"
          + "        <d:href>{href}</d:href>"
          + "        <d:depth>infinity</d:depth>"
          + "      </d:scope>"
          + "    </d:from>"
          + "    <d:where>"
          + "      <d:like> "
          + "        <d:prop><d:getcontenttype/></d:prop>"
          + "        <d:literal>video/%</d:literal>"
          + "      </d:like>"
          + "    </d:where>"
          + "  </d:basicsearch>"
          + "</d:searchrequest>";

  public static final String VIDEO_SEARCH_QUEUE_DASL
          = "<?xml version=\"1.0\" ?>"
          + "  <d:searchrequest xmlns:d=\"DAV:\" xmlns:m=\"" + CUSTOM_PROPERTIES_NAMESPACE + "\">"
          + "  <d:basicsearch>"
          + "    <d:select>"
          + "      <d:allprop/>"
          + "    </d:select>"
          + "    <d:from>"
          + "      <d:scope>"
          + "        <d:href>{href}</d:href>"
          + "        <d:depth>infinity</d:depth>"
          + "      </d:scope>"
          + "    </d:from>"
          + "    <d:where>"
          + "      <d:and>"
          + "        <d:eq> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_MIMETYPE.name + "/></d:prop>"
          + "          <d:literal>video/mp4</d:literal>"
          + "        </d:eq>"
          + "        <d:gt> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_MEDIADATARATE.name + "/></d:prop>"
          + "          <d:literal>" + THRESHOLD_RATE + "</d:literal>"
          + "        </d:gt>"
          + "        <d:eq> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_RECOMPRESSION.name + "/></d:prop>"
          + "          <d:literal>-1</d:literal>"
          + "        </d:eq>"
          + "      </d:and>"
          + "    </d:where>"
          + "  </d:basicsearch>"
          + "</d:searchrequest>";

  public static final String VIDEO_SEARCH_ALREADY_DONE_DASL
          = "<?xml version=\"1.0\" ?>"
          + "  <d:searchrequest xmlns:d=\"DAV:\" xmlns:s=\"http://www.xythos.com/namespaces/StorageServer\">"
          + "  <d:basicsearch>"
          + "    <d:select>"
          + "      <d:prop><m:" + CUSTOM_PROPERTY_RECOMPRESSION.name + "/></d:prop>"
          + "    </d:select>"
          + "    <d:from>"
          + "      <d:scope>"
          + "        <d:href>{href}</d:href>"
          + "        <d:depth>infinity</d:depth>"
          + "      </d:scope>"
          + "    </d:from>"
          + "    <d:where>"
          + "      <d:and>"
          + "        <d:eq> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_ORIGINALDIGEST.name + "/></d:prop>"
          + "          <d:literal>{originaldigest}</d:literal>"
          + "        </d:eq>"
          + "        <d:eq> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_ORIGINALSIZE.name + "/></d:prop>"
          + "          <d:literal>{originalsize}</d:literal>"
          + "        </d:eq>"
          + "      </d:and>"
          + "    </d:where>"
          + "  </d:basicsearch>"
          + "</d:searchrequest>";

  public static final String VIDEO_SEARCH_RESET_DASL
          = "<?xml version=\"1.0\" ?>"
          + "  <d:searchrequest xmlns:d=\"DAV:\" xmlns:m=\"" + CUSTOM_PROPERTIES_NAMESPACE + "\">"
          + "  <d:basicsearch>"
          + "    <d:select>"
          + "      <d:allprop/>"
          + "    </d:select>"
          + "    <d:from>"
          + "      <d:scope>"
          + "        <d:href>{href}</d:href>"
          + "        <d:depth>infinity</d:depth>"
          + "      </d:scope>"
          + "    </d:from>"
          + "    <d:where>"
          + "      <d:or>"
          + "       {isdefinedlist}"
          + "      </d:or>"
          + "    </d:where>"
          + "  </d:basicsearch>"
          + "</d:searchrequest>";
  
  
  public static final int RECOMPRESSION_DEFAULT_QUALITY = 40;
  
  
  private String query;
  private String queuequery;
  private String resetquery;
  
  public String servername;
  public String action;
  public String option;
  public String regex;

  transient Calendar calends;  
  transient HashMap<LocalPropertyDefinition,PropertyDefinition> davpropertydefs;
  transient ArrayList<String> list;
  transient Pattern filespattern;
  transient File base, executablefile;
  
  transient PropertyDefinition digestpropdef;

  @JsonCreator
  public AnalyseVideoTask( 
          @JsonProperty( "search" ) String servername, 
          @JsonProperty( "action" ) String action, 
          @JsonProperty( "option" ) String option, 
          @JsonProperty( "regex"  ) String regex )
  {
    this.servername = servername;
    this.action     = action;
    this.option     = option;
    this.regex      = regex;
    query = VIDEO_SEARCH_DASL;
    query = query.replace("{href}", "https://" + servername + "/bbcswebdav/");
    queuequery = VIDEO_SEARCH_QUEUE_DASL;
    queuequery = queuequery.replace("{href}", "https://" + servername + "/bbcswebdav/");
    resetquery = VIDEO_SEARCH_RESET_DASL;
    resetquery = resetquery.replace("{href}", "https://" + servername + "/bbcswebdav/");
    StringBuilder isdefinedbuilder = new StringBuilder();
    for ( LocalPropertyDefinition def : localpropdefs )
    {
      isdefinedbuilder.append( "        <d:is-defined> " );
      isdefinedbuilder.append( "          <d:prop><m:" );
      isdefinedbuilder.append(                       def.name );
      isdefinedbuilder.append(                               "/></d:prop>" );
      isdefinedbuilder.append( "        </d:is-defined>" );
    }
    resetquery = resetquery.replace( "{isdefinedlist}", isdefinedbuilder.toString() );
  }

  @Override
  public void doTask() throws InterruptedException
  {
    base = webappcore.pluginbase.toFile();
    executablefile = new File( base, "ffmpegbin/ffmpeg" );

    int analyses = 0;
    list = new ArrayList<>();
    calends = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    davpropertydefs = new HashMap<>();
    filespattern = Pattern.compile( regex ); // "/courses/\\d+-21\\d\\d/." );
    Path logfile = webappcore.logbase.resolve("videoanalysis-" + webappcore.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");

    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      webappcore.logger.info("Analyse video files (" + action + "), using regex, " + filespattern + ".  Process started. May take many minutes. ");
      long start = System.currentTimeMillis();

      prepare(log);
      boolean recompress = "recompress".equals(action);
      boolean fullreset = "clear".equals(action) && "reset".equals( option );
      String q;
      
      if ( recompress )
        q = queuequery;
      else if ( fullreset )
        q = resetquery;
      else
        q = query;
      webappcore.logger.info( q );
      
      list = findVideoFiles(log, q, fullreset?null:filespattern);
      if ( list == null )
      {
        webappcore.logger.info("Unable to build list of video files. ");
      } else
      {
        webappcore.logger.info("Completed list of video files. Size = " + list.size());
        for (String id : list)
        {
          if (Thread.interrupted())
            throw new InterruptedException();


          if ( "analyse".equals(action) )
          {
            if ( !analyseOne( log, id ) )
            { 
              webappcore.logger.error("Analysis failed so halting task." );
              return;
            }
          }
          if ("clear".equals(action))
            clearOne( log, id, "reset".equals( option ) );
          if ("list".equals(action))
            listOne(log, id);
          if ( recompress )
            recompressOne( log, id );

          log.flush();
          analyses++;
//          if ( analyses > 1000)
//          {
//            webappcore.debuglogger.info("Halting because reached file count limit. ");
//            break;
//          }
          long now = System.currentTimeMillis();
          if ((now - start) > (1L * 60L * 60L * 1000L))
          {
            webappcore.logger.info("Halting because reached elapsed time limit. ");
            break;
          }
        }
        if ( "clear".equals( action ) && "reset".equals( option ) )
          reset( log );
      }
      
      long end = System.currentTimeMillis();
      float elapsed = 0.001f * (float) (end - start);
      webappcore.logger.info("Analyse video file process ended after " + elapsed + " seconds. ");
    } catch (XythosException | IOException ex)
    {
      webappcore.logger.error("Analyse video file process failed ", ex );
    }
  }

  void prepare(PrintWriter log) throws XythosException
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdmin("VideoAnalysis");      
      
      digestpropdef = PropertyDefinitionManager.findPropertyDefinition(
                "http://www.xythos.com/namespaces/StorageServer", 
                "digest", 
                context );
      
      for ( int i=0; i<AnalyseVideoTask.localpropdefs.length; i++ )
      {
        LocalPropertyDefinition def = AnalyseVideoTask.localpropdefs[i];
        PropertyDefinition p = PropertyDefinitionManager.findPropertyDefinition(
                CUSTOM_PROPERTIES_NAMESPACE, 
                def.name, 
                context );
        // Don't think this goes into database until the first actual
        // property value is set. (Since no context is required for this call.)
        if ( p == null )
          p = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE,
                def.name,
                def.type,
                false, // not versioned 
                true,  // readable
                true,  // writable
                false, // not caseinsensitive
                false, // not protected
                false, // not full text indexed
                def.description );
        if ( p==null )
          webappcore.logger.info( "Unable to create property definition " + def.name );
        else
          davpropertydefs.put( AnalyseVideoTask.localpropdefs[i], p );
      }
      webappcore.logger.info( "Number of prop defs in davpropertydefs = " + davpropertydefs.size() );
    }
    finally
    {
      try { if (context != null) context.commitContext(); }
      catch (XythosException ex) { webappcore.logger.error("Error occured trying to commit xythos context.", ex); }
    }
  }

  
  void reset(PrintWriter log) throws XythosException
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdmin("VideoAnalysis");
      for ( PropertyDefinition pdef : davpropertydefs.values() )
        if ( pdef != null )
          PropertyDefinitionManager.deletePropertyDefinition( pdef, context );
    }
    finally
    {
      try {if (context != null) { context.commitContext(); }}
      catch (XythosException ex) {webappcore.logger.error("Error occured trying to commit xythos context.", ex);}
    }
  }

  String findIdenticalVideoFileEntryId( PrintWriter log, String originaldigest, long originalsize, Context context )
          throws StorageServerException
  {
    String alreadydonequery = VIDEO_SEARCH_ALREADY_DONE_DASL.replace( "{originaldigest}", originaldigest );
    alreadydonequery = alreadydonequery.replace( "{originalsize}", Long.toString(originalsize) );
    ArrayList<String> list = findVideoFiles( log, alreadydonequery, null );
    if ( list == null || list.size() == 0 )
      return null;
    return list.get( 0 );
  }
  
  ArrayList<String> findVideoFiles(PrintWriter log, String query, Pattern filepathpattern)
  {
    ArrayList<String> locallist = new ArrayList<>();
    webappcore.logger.info( "findVideoFiles " +  filepathpattern );
    if ( filepathpattern != null )
      webappcore.logger.info( "findVideoFiles " +  filepathpattern.pattern() );
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      DaslStatement statement = new DaslStatement(query, context);
      DaslResultSet resultset = statement.executeDaslQuery();

      while (resultset.nextEntry())
      {
        FileSystemEntry fse = resultset.getCurrentEntry();
        if (!(fse instanceof com.xythos.fileSystem.File))
        {
          if ( log != null )
            log.println( "Skipping non-file entry " + fse.getName() );
          continue;
        }
        com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
        // Ignore any files that have or will have versions
        if ( f.isRevisionable() || f.getFileVersionIDs().length > 1 )
        {
          if ( log != null )
            log.println( "Skipping revisionable file " + fse.getName() );
          continue;
        }
        
        //bbmonitor.debuglogger.info( "Found video file " + fse.getName() );
        
        if ( filepathpattern == null || filepathpattern.matcher( fse.getName() ).matches() )
          locallist.add( fse.getEntryID() );
        else
          if ( log != null )
            log.println( "Filtering out " + fse.getName() );
      }
    } catch (Throwable th)
    {
      webappcore.logger.error("Error occured while finding list of video files.", th);
      return null; // error so stop processing
    } finally
    {
      try
      {
        if (context != null)
        {
          context.commitContext();
        }
      } catch (XythosException ex)
      {
      }
    }
    return locallist;
  }

  void clearOne( PrintWriter log, String id, boolean full )
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      FileSystemEntry fse = FileSystem.findEntryFromEntryID(id, false, context);
      if (fse == null)
      {
        return;
      }
      if (!(fse instanceof com.xythos.fileSystem.File))
      {
        return;
      }
      com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;

      // Delete all davpropertydefs within our namespace
      // except the one that flags that the video has already been
      // recompressed.
      Property[] props = f.getProperties( false, context );
      for ( Property p : props )
      {
        if ( CUSTOM_PROPERTIES_NAMESPACE.equals( p.getNamespaceName() ) )
        {
          // Check against local list of properties
          for ( LocalPropertyDefinition local : AnalyseVideoTask.localpropdefs )
          {
            if ( local.name.equals( p.getName() ) )
              if ( full || !local.recompression )
                f.deleteProperty( p, false, context );
          }
        }
      }
      log.println( "DONE\t" + f.getName() );
    }
    catch (Throwable th)
    {
      webappcore.logger.error("Error occured while clearing etag property on video files.", th);
      return; // error so stop processing
    } finally
    {
      try
      {
        if (context != null)
        {
          context.commitContext();
        }
      } catch (XythosException ex)
      {
      }
    }
    return;
  }

  
  /**
   * Analyse a file or copy analysis from an identical file.
   * 
   * @param log
   * @param targets
   * @return 
   */
  boolean analyseOne( PrintWriter log, String entryid )
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      FileSystemEntry fse = FileSystem.findEntryFromEntryID( entryid, false, context);
      if (fse == null)
        return true;
      if (!(fse instanceof com.xythos.fileSystem.File))
        return true;
      com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
      Revision r = f.getLatestRevision();
      webappcore.logger.info("Found video file " + fse.getName());

      VideoSearchResult vsr = new VideoSearchResult();
      vsr.etag = r.getETagValue();
      Property aetag = f.getProperty(davpropertydefs.get(CUSTOM_PROPERTY_ANALYSEDETAG), true, context);
      if (aetag != null)
      {
        vsr.analysedetag = aetag.getValue();
        webappcore.logger.debug("Comparing " + vsr.etag + " with " + vsr.analysedetag);
        if (vsr.analysedetag.equals(vsr.etag))
        {
          webappcore.logger.debug("Analysis is up to date.");
          return true;
        }
      }

      vsr.analysedetag = vsr.etag;
      vsr.fileid = f.getID();
      vsr.blobid = r.getBlobID();
      vsr.path = f.getName();
      vsr.size = r.getSize();
      vsr.recordedmimetype = f.getFileMimeType();
      
      String alreadydoneentryid=null;
      Property  pdigest = f.getProperty( digestpropdef, true, context );
      if ( pdigest != null )
        alreadydoneentryid = findIdenticalVideoFileEntryId( null, pdigest.getValue(), f.getEntrySize(), context );

      if ( alreadydoneentryid != null )
      {
        VideoSearchResult othervsr = readVideoMetadata( alreadydoneentryid, context );
        applyFileProperties( entryid, othervsr, context );
        log.print( "COPIED\t" );
        log.println( f.getName() );
        log.print( "PROPERTIES\t" );
        log.println( vsr.path );
      }
      else
      {
        analyseVideoFile( vsr, r );
        applyFileProperties( entryid, vsr, context );      
        log.print( "ANALYSED\t" );
        log.println( f.getName() );
        log.print( "PROPERTIES\t" );
        log.println( f.getName() );
      }

    }
    catch (Throwable th)
    {
      webappcore.logger.error("Error occured while running analysis of video files.", th);
      return false; // error so stop processing
    }
    finally
    {
      try
      {
        if (context != null) { context.commitContext(); }
      }
      catch (XythosException ex)
      {
        webappcore.logger.error("Error occured while running analysis of video files.", ex);
        return false;
      }
    }
    return true; // there might be more data to process...
  }

  void applyFileProperties( String entryid, VideoSearchResult vsr, Context context ) throws StorageServerException, PropertyException, XythosException
  {
    FileSystemEntry fse = FileSystem.findEntryFromEntryID( entryid, false, context);
    if (fse == null)
      return;
    if (!(fse instanceof com.xythos.fileSystem.File))
      return;
    com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;

    if (vsr.detectedmimetype != null)
      replaceShortStringProperty( f, CUSTOM_PROPERTY_MIMETYPE, vsr.detectedmimetype, context );

    if (vsr.duration >= 0)
      replaceLongProperty( f, CUSTOM_PROPERTY_MEDIADURATION, vsr.duration, context );

    if (vsr.duration > 0 && vsr.size > 0)
      replaceLongProperty( f, CUSTOM_PROPERTY_MEDIADATARATE, vsr.datarate, context );

    if (vsr.medialog != null)
      replaceStringProperty( f, CUSTOM_PROPERTY_MEDIALOG, vsr.medialog, context );

    replaceLongProperty( f, CUSTOM_PROPERTY_RECOMPRESSION, -1, context );

    // finish off by marking the file as analysed
    replaceShortStringProperty( f, CUSTOM_PROPERTY_ANALYSEDETAG, vsr.etag, context );
    
    webappcore.logger.debug( "  Updated file properties." );
  }

  void replaceShortStringProperty( com.xythos.fileSystem.File f, LocalPropertyDefinition def, String value, Context context ) 
          throws StorageServerException, PropertyException, PropertyWriteException, 
          UnsupportedPropertyException, DuplicatePropertyException, PropertyValueFormatException, 
          InvalidPropertyValueException
  {
    PropertyDefinition propdef = davpropertydefs.get( def );
    if ( propdef == null )
    {
      webappcore.logger.error( "Don't have property definition " + def.name );
      throw new NullPointerException( "Don't have property definition." );
    }
    Property p = f.getProperty( propdef, true, context );
    if ( p != null )
      f.deleteProperty( p, false, context );
    f.addShortStringProperty( propdef, value, false, context);    
  }
  
  void replaceStringProperty( com.xythos.fileSystem.File f, LocalPropertyDefinition def, String value, Context context )
          throws StorageServerException, PropertyException, PropertyWriteException, 
          UnsupportedPropertyException, DuplicatePropertyException, PropertyValueFormatException, 
          InvalidPropertyValueException
  {
    PropertyDefinition propdef = davpropertydefs.get( def );
    if ( propdef == null )
    {
      webappcore.logger.error( "Don't have property definition " + def.name );
      throw new NullPointerException( "Don't have property definition." );
    }
    Property p = f.getProperty( propdef, true, context );
    if (p != null)
      f.deleteProperty(p, false, context);
    f.addStringProperty(propdef, value, false, context);        
  }
  
  void replaceLongProperty( com.xythos.fileSystem.File f, LocalPropertyDefinition def, long value, Context context )
          throws StorageServerException, PropertyException, PropertyWriteException, 
          UnsupportedPropertyException, DuplicatePropertyException, PropertyValueFormatException, 
          InvalidPropertyValueException
  {
    PropertyDefinition propdef = davpropertydefs.get( def );
    if ( propdef == null )
    {
      webappcore.logger.error( "Don't have property definition " + def.name );
      throw new NullPointerException( "Don't have property definition." );
    }
    Property p = f.getProperty( propdef, true, context );
    if (p != null)
      f.deleteProperty(p, false, context);
    f.addLongProperty(propdef, value, false, context);        
  }
  
  
  void setRecompressionFailedProperty( String entryid, Context context ) throws StorageServerException, PropertyException, XythosException
  {
    FileSystemEntry fse = FileSystem.findEntryFromEntryID( entryid, false, context);
    if (fse == null)
      return;
    if (!(fse instanceof com.xythos.fileSystem.File))
      return;
    com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
    webappcore.logger.info( "Setting failed compression metadata to " + f.getName() );    
    replaceLongProperty( f, CUSTOM_PROPERTY_RECOMPRESSION, -2, context );
  }  
  
  void setRecompressionSucceededProperty( String entryid, long value, String originaldigest, long originalsize, Context context ) throws StorageServerException, PropertyException, XythosException
  {
    FileSystemEntry fse = FileSystem.findEntryFromEntryID( entryid, false, context);
    if (fse == null)
      return;
    if (!(fse instanceof com.xythos.fileSystem.File))
      return;
    com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
    webappcore.logger.info( "Applying video metadata to " + f.getName() );
    
    replaceLongProperty(        f, CUSTOM_PROPERTY_RECOMPRESSION,  value,          context );
    replaceLongProperty(        f, CUSTOM_PROPERTY_ORIGINALSIZE,   originalsize,   context );
    replaceShortStringProperty( f, CUSTOM_PROPERTY_ORIGINALDIGEST, originaldigest, context );
  }  
  
  
  private static int n=100000;
  void recompressOne(PrintWriter log, String entryid ) throws InterruptedException
  {
    boolean success;
    VideoCloneTarget newtarget;
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      FileSystemEntry fse = FileSystem.findEntryFromEntryID( entryid, false, context );
      if (fse == null)
        return;
      if (!(fse instanceof com.xythos.fileSystem.File))
        return;
      com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
      com.xythos.fileSystem.File updatedfile = null;
      Revision r = f.getLatestRevision();
      webappcore.logger.info("Found video file to recompress: " + fse.getName());
      webappcore.logger.info("File ID: " + f.getID() );
      webappcore.logger.info("Blob ID: " + r.getBlobID() );
      webappcore.logger.info("File docstore name: " + f.getDocstoreName() );
      webappcore.logger.info("Revision storage name: " + r.getStorageName() );
      
      String etag = r.getETagValue();
      Property aetag = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_ANALYSEDETAG ), true, context);
      if (aetag != null)
      {
        String analysedetag = aetag.getValue();
        webappcore.logger.info( "Comparing " + etag + " with " + analysedetag );
        if ( !analysedetag.equals( etag ) )
        {
          webappcore.logger.info( "Video file changed since it was last analysed so can't recompress now." );
          return;
        }
      }
      
      Property p = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_RECOMPRESSION ), true, context);
      if (p == null)
      {
        webappcore.logger.info( "File has no recompression property set." );
        return;
      }        
      if ( !"-1".equals( p.getValue() ) )
      {
        webappcore.logger.info( "Recompression  is not -1 so has already been recompressed (or it previously failed.)" );
        return;
      }        
      p = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_MEDIADATARATE ), true, context );
      if (p == null)
      {
        webappcore.logger.info( "File has no data rate set." );
        return;
      }        
      long rate = Long.parseLong( p.getValue() );
      webappcore.logger.info( "Data rate = " + rate );
      if ( rate < THRESHOLD_RATE )
      {
        webappcore.logger.info( "File is already compressed enough." );
        return;
      }
      webappcore.logger.info( "File is suitable to compress." );
      
      Property  pdigest = f.getProperty( digestpropdef, true, context );
      String originaldigest = pdigest.getValue();
      long originalsize = f.getEntrySize();
      String alreadydoneentryid = findIdenticalVideoFileEntryId( null, originaldigest, originalsize, context );
      
      // If already done clone the blob otherwise use ffmpeg to compress.
      if ( alreadydoneentryid != null )
      {
        long sourcerecompression = -3;
        try
        {
          FileSystemEntry sourcefse = FileSystem.findEntryFromEntryID( alreadydoneentryid, false, context );
          if (sourcefse == null)
            return;
          if (!(sourcefse instanceof com.xythos.fileSystem.File))
            return;
          com.xythos.fileSystem.File sourcefile = (com.xythos.fileSystem.File) sourcefse;
          LongProperty sourceprop = (LongProperty)f.getProperty( davpropertydefs.get( CUSTOM_PROPERTY_RECOMPRESSION ), true, context );
          sourcerecompression = sourceprop.getLongValue();

          com.xythos.fileSystem.File updatedclone = 
            (com.xythos.fileSystem.File)sourcefile.copyNode(
                f.getLatestFileVersion(), 
                f.getVirtualServer(), 
                FileSystemUtil.getParentFullName( f.getName() ), 
                FileSystemUtil.getBaseName( f.getName() ), 
                f.getOwnerPrincipalID(), 
                1, 
                true, // overwrite
                DirectoryEntry.TRASH_OP.NONE, 
                false,  // no recurse, not a directory
                false   // do not copy dead davpropertydefs (etag will signal video props are out of date.)
          );
          webappcore.logger.info( "Copied blob from " + sourcefile.getEntryID() + " onto " + f.getEntryID() + " producing " + updatedclone.getEntryID() );
          success = true;
        }
        catch ( Exception ex )
        {
          webappcore.logger.error("Cloning recompressed video file failed. " + entryid );
          success = false;
        }
        
        if ( success )
        {
          log.print( "SUCCESS\t" );
          //log.print( compression );
          log.print( "%\t" );
          log.println( f.getName() );
          setRecompressionSucceededProperty( entryid, sourcerecompression, originaldigest, originalsize, context );
        }
        else
        {
          log.print( "FAIL\t" );
          log.print( alreadydoneentryid );
          log.print( "\t" );
          log.println( f.getName() );  
          // mark failure of compression so we don't keep trying
          setRecompressionFailedProperty( entryid, context );
        }
        
      }
      else
      {
        n++;
        File file         = new File( base, "in.mp4" );
        File logfile = new File( base, "ffmpeg_log_" + n + ".txt" );
        if ( logfile.exists() )
          logfile.delete();
        File recompressedfile        = new File( base, "out_" + n + ".mp4"          );
        success = writeRevisionToLegacyFile( r, file );
        int ffmpegexitcode=-1;
        if ( success )
        {
          ffmpegexitcode = compressToFile( file, recompressedfile, logfile );
          success = ffmpegexitcode == 0;
        }
      
        if ( success )
        {
          // overwrite original xythos file...
           updatedfile = overwriteXythosFile( f, recompressedfile, context, "video/mp4" );
           newtarget = new VideoCloneTarget( updatedfile );
           success = updatedfile != null;
        }
        
        if ( success )
        {
          int compression = Math.round( 100.0f * (float)recompressedfile.length() / (float)file.length() );
          log.print( "SUCCESS\t" );
          log.print( compression );
          log.print( "%\t" );
          log.println( f.getName() );
          setRecompressionSucceededProperty( entryid, RECOMPRESSION_DEFAULT_QUALITY, originaldigest, originalsize, context );
        }
        else
        {
          log.print( "FAIL\t" );
          log.print( ffmpegexitcode );
          log.print( "\t" );
          log.println( f.getName() );  
          // mark failure of compression so we don't keep trying
          setRecompressionFailedProperty( entryid, context );
        }
      }

      
    }
    catch ( InterruptedException iex )
    {
      webappcore.logger.error("Recompression interrupted." );
      throw iex;
    }
    catch (Throwable th)
    {
      webappcore.logger.error("Error occured while running analysis of video files.", th);
      return; // error so stop processing
    }
    finally
    {
      try
      {
        if (context != null)
        {
          context.commitContext();
        }
      } catch (XythosException ex)
      {
        webappcore.logger.error("Error occured trying to commit context.", ex);
        return;
      }
    }
    return; // there might be more data to process...
  }

  
  public com.xythos.fileSystem.File overwriteXythosFile( com.xythos.fileSystem.File f, File source, Context context, String mime )
  {
    try ( FileInputStream fin = new FileInputStream( source ); )
    {
      BlobCreatorFromInputStream blobby = BlobCreator.createInstance(fin);
      String parentpath = FileSystemUtil.getParentFullName( f.getName() );                
      return com.xythos.fileSystem.File.create(
                  f.getVirtualServer(), 
                  parentpath, 
                  f.getName().substring(f.getName().lastIndexOf( '/' )+1 ), 
                  (ContextImpl)context, 
                  mime, 
                  false,                        // not revisionable
                  false,                        // not logging
                  true,                         // yes - overwrite
                  f.getOwnerPrincipalID(),      // same owner
                  false,                        // not bandwidth used
                  blobby, 
                  f.getDocumentClass(),         // same document class
                  f.getRetentionSchedule(), 
                  FileSystem.UnicodeNorm.OVERWRITE );
    } catch (IOException | XythosException ex)
    {
      webappcore.logger.error( "Unable to put data into Xythos File.", ex );
      return null;
    }
  }
  
  
  public boolean writeRevisionToLegacyFile( Revision r, File file )
  {
    try ( FileOutputStream fout = new FileOutputStream( file ) )
    {
      r.getBytes( fout, true, false, false );
      return true;
    }
    catch (IOException | StorageServerException ex)
    {
      return false;
    }
  }
  
  public int compressToFile( File infile, File outfile, File logfile ) throws InterruptedException
  {
    Process p = null;
    try
    {
      if ( outfile.exists() )
        outfile.delete();
      ProcessBuilder pb = new ProcessBuilder( 
              executablefile.getAbsolutePath(), 
              "-nostdin", 
              //"-nostats",
              "-i", infile.getAbsolutePath(),
              "-c:v", "libx264", 
              "-crf", Integer.toString( RECOMPRESSION_DEFAULT_QUALITY ),
              outfile.getAbsolutePath()
      );
      pb.directory( executablefile.getParentFile() );
      pb.environment().put( "LD_LIBRARY_PATH", "." );
      pb.redirectErrorStream( true );
      pb.redirectOutput( ProcessBuilder.Redirect.appendTo( logfile ) );
      p = pb.start();
      while ( p.isAlive() )
      {
        webappcore.logger.debug( "ffmpeg still running." );
        p.waitFor( 2, TimeUnit.SECONDS );
      }
      webappcore.logger.info( "ffmpeg exit code = " + p.exitValue() );
      return p.exitValue();
    }
    catch ( InterruptedException ie )
    {
      if ( p != null )
      {
        p.destroy();
        webappcore.logger.error( "Cancelled ffmpeg process because this task was interrupted." );
      }
      throw ie;
    }
    catch ( Exception ex )
    {
      webappcore.logger.error( "Exception while trying to run ffmpeg", ex );
    }
    return -1;
  }  

  void listOne(PrintWriter log, String id)
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      VideoSearchResult vsr = readVideoMetadata( id, context );
      if ( vsr == null )
        return;
//        p = f.getProperty(propdefmedialog, true, context);
//        if ( p != null )
//          webappcore.debuglogger.info( p.getValue() );
      log.append( vsr.toString() );
    }
    catch (Throwable th)
    {
      webappcore.logger.error("Error occured while running analysis of video files.", th);
      return; // error so stop processing
    } finally
    {
      try
      {
        if (context != null)
        {
          context.commitContext();
        }
      } catch (XythosException ex)
      {
        webappcore.logger.error("Error occured while running analysis of video files.", ex);
        return;
      }
    }
  }
  
  VideoSearchResult readVideoMetadata( String id, Context context ) throws StorageServerException, InvalidRequestException, XythosException
  {
    VideoSearchResult vsr=null;
    
    FileSystemEntry fse = FileSystem.findEntryFromEntryID(id, false, context);
    if (fse == null)
    {
      return null;
    }
    if (!(fse instanceof com.xythos.fileSystem.File))
    {
      return null;
    }
    com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
    Revision r = f.getLatestRevision();

    vsr = new VideoSearchResult();
    vsr.etag = r.getETagValue();
    Property aetag = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_ANALYSEDETAG ), true, context);
    if (aetag != null)
    {
      vsr.analysedetag = aetag.getValue();
    }
    vsr.blobid = r.getBlobID();
    vsr.path = f.getName();
    vsr.size = r.getSize();
    vsr.recordedmimetype = f.getFileMimeType();

    Property p = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_MEDIADURATION ), true, context);
    if (p != null)
    {
      try
      {
        vsr.duration = Integer.valueOf(p.getValue());
      } catch (NumberFormatException nfe)
      {
        vsr.duration = -1;
      }
    }
    p = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_MEDIADATARATE ), true, context);
    if (p != null)
    {
      try
      {
        vsr.datarate = Integer.valueOf(p.getValue());
      } catch (NumberFormatException nfe)
      {
        vsr.datarate = -1;
      }
    }

    p = f.getProperty(davpropertydefs.get( CUSTOM_PROPERTY_MIMETYPE ), true, context);
    if (p != null)
    {
      vsr.detectedmimetype = p.getValue();
    }

    return vsr;
  }

  boolean analyseVideoFile( VideoSearchResult vsr, File f )
  {
    try ( FileInputStream in = new FileInputStream( f ) )
    {
      analyseVideoFile( vsr, in, f.length() );      
    }
    catch (Exception ex)
    {
      webappcore.logger.error("Problem reading metadata in media file.", ex);
      return false;
    }
    return true;
  }
  
  boolean analyseVideoFile(VideoSearchResult vsr, Revision r)
  {
    try (XythosAdapterChannel channel = new XythosAdapterChannel(r);
            InputStream in = Channels.newInputStream(channel);)
    {
      return analyseVideoFile( vsr, in, r.getSize() );
    }
    catch (Exception ex)
    {
      webappcore.logger.error("Problem reading metadata in media file.", ex);
      return false;
    }
  }
  
  boolean analyseVideoFile( VideoSearchResult vsr, InputStream in, long size )
          throws IOException, ImageProcessingException
  {
    StringBuilder message = new StringBuilder();

    Metadata metadata = ImageMetadataReader.readMetadata(in);
    if (metadata == null)
    {
      vsr.medialog = "No metadata found for file.";
      return true;
    }

    message.append("File metadata:\n");
    FileTypeDirectory ftdirectory = metadata.getFirstDirectoryOfType(FileTypeDirectory.class);
    if ( metadata == null )
    {
      vsr.medialog = "No file type found in metadata.";
      return true;
    }
    String propermime = ftdirectory.getString(FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE);
    vsr.detectedmimetype = propermime;
    if (null == vsr.detectedmimetype)
    {
      message.append("Unrecognised mime type.\n");
      return true;
    }
    
    switch (vsr.detectedmimetype)
    {
      case "video/quicktime":
        QuickTimeDirectory qt4d = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
        if (qt4d == null)
          webappcore.logger.error("File has no quicktime metadata.");
        else
          try { vsr.duration = qt4d.getInt(QuickTimeDirectory.TAG_DURATION_SECONDS); }
          catch (MetadataException ex) { webappcore.logger.error("Error attempting to read video metadata.", ex); }
        break;
      case "video/mp4":
        Mp4Directory mp4d = metadata.getFirstDirectoryOfType(Mp4Directory.class);
        if (mp4d == null)
          webappcore.logger.error("File has no mp4 metadata.");
        else
          try { vsr.duration = mp4d.getInt(Mp4Directory.TAG_DURATION_SECONDS); }
          catch (MetadataException ex) { webappcore.logger.error("Error attempting to read video metadata.", ex); }
        break;
      case "video/vnd.avi":
        AviDirectory avi4d = metadata.getFirstDirectoryOfType(AviDirectory.class);
        if (avi4d == null)
          webappcore.logger.error("File has no avi metadata.");
        else
          try { vsr.duration = avi4d.getInt(AviDirectory.TAG_DURATION) / 1000000; }
          catch (MetadataException ex) { webappcore.logger.error("Error attempting to read video metadata.", ex); }
        break;
      default:
        message.append("Unrecognised mime type.\n");
        break;
    }

    if ( vsr.duration > 0 && size > 0 )
      vsr.datarate = size / vsr.duration;
    else
      vsr.datarate = -1;

    for (com.drew.metadata.Directory directory : metadata.getDirectories())
      for (com.drew.metadata.Tag tag : directory.getTags())
      {
        message.append( tag.toString().replace( "\u0000", "[0x00]" ) );
        message.append("\n");
      }
    vsr.medialog = message.toString();
    //bbmonitor.debuglogger.debug(vsr.medialog);
    return true;
  }

  VideoCloneTarget[] getCloneTargets( String fileid, Pattern filepathpattern )
  {
    com.xythos.fileSystem.File f=null;
    Context context=null;
    
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      FileSystemEntry fse = FileSystem.findEntryFromEntryID(fileid, false, context);
      if (fse == null)
        return null;
      if (!(fse instanceof com.xythos.fileSystem.File))
        return null;
      f = (com.xythos.fileSystem.File) fse;
      Revision r = f.getLatestRevision();
      return getCloneTargets( f, filepathpattern, context );
    }
    catch (XythosException ex)
    {
      webappcore.logger.error( "getCloneTargets()", ex );
      return null;
    }
    finally
    {
      try
      {
        if (context != null)
          context.commitContext();
      }
      catch (XythosException ex)
      {
        webappcore.logger.error( "getCloneTargets() unable to commit context.", ex );
      }      
    }
  }
  
  VideoCloneTarget[] getCloneTargets( com.xythos.fileSystem.File f, Pattern filepathpattern, Context context ) throws StorageServerException, InvalidRequestException, InternalException
  {
    Revision r = f.getLatestRevision();
    Revision[] r_others = Revision.findAssociatedRevisions( r.getBlobID(), context, f.getDocstoreName() );
    ArrayList<VideoCloneTarget> list = new ArrayList<>();
    for ( Revision r_other : r_others )
    {
      com.xythos.fileSystem.File f_other = r_other.getFile();
      // exclude files that have multiple versions - too complicated to deal with those.
      if ( f_other.isRevisionable() || f_other.getFileVersionIDs().length > 1 )
        continue;
      VideoCloneTarget target = new VideoCloneTarget( f_other );
      webappcore.logger.debug("  Associated Revision: " + target.entryid + " " + target.name + " " + target.etag );
      if ( filepathpattern == null || filepathpattern.matcher( target.name ).matches() )
        list.add( target );
    }
    // make sure list items are in a consistent order
    list.sort( new Comparator<VideoCloneTarget>()
                      {
                        @Override
                        public int compare(VideoCloneTarget o1, VideoCloneTarget o2)
                        {
                          return o1.name.compareTo( o2.name );
                        }
                      }
    );
    return list.toArray( new VideoCloneTarget[ list.size() ] );
  }
  
  class VideoCloneTarget
  {
    String name;
    String entryid;
    String etag;
    String ownerid;

    public VideoCloneTarget( com.xythos.fileSystem.File f ) throws StorageServerException
    {
      this.name    = f.getName();
      this.entryid = f.getEntryID();
      this.etag    = f.getETag();
      this.ownerid = f.getOwnerPrincipalID();
    }
    
  }
}

class LocalPropertyDefinition
{
  String name;
  int type;
  String description;
  boolean recompression;
  
  public LocalPropertyDefinition( String name, int type, String description, boolean recompression )
  {
    this.name = name;
    this.type = type;
    this.description = description;
    this.recompression = recompression;
  }

  @Override
  public boolean equals(Object obj)
  {
    if ( !(obj instanceof LocalPropertyDefinition) )
      return false;
    LocalPropertyDefinition other = (LocalPropertyDefinition)obj;
    return this.name.equals( other.name );
  }

  @Override
  public int hashCode()
  {
    return name.hashCode();
  }  
}

