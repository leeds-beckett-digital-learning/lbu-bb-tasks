/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.xythos.common.InternalException;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.Revision;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.InvalidRequestException;
import com.xythos.storageServer.api.StorageServerException;
import com.xythos.storageServer.properties.api.Property;
import com.xythos.storageServer.properties.api.PropertyDefinition;
import com.xythos.storageServer.properties.api.PropertyDefinitionManager;
import com.xythos.webdav.dasl.api.DaslResultSet;
import com.xythos.webdav.dasl.api.DaslStatement;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import uk.ac.leedsbeckett.bbcswebdavmonitor.VideoSearchResult;
import uk.ac.leedsbeckett.bbcswebdavmonitor.XythosAdapterChannel;

/**
 *
 * @author jon
 */
public class AnalyseVideoTask extends BaseTask
{

  public static final String CUSTOM_PROPERTIES_NAMESPACE = "my.leedsbeckett.ac.uk/mediaanalysis";
  public static final String CUSTOM_PROPERTY_ANALYSEDETAG = "analysedetag";
  public static final String CUSTOM_PROPERTY_MEDIADATARATE = "mediadatarate";
  public static final String CUSTOM_PROPERTY_MEDIADURATION = "mediaduration";
  public static final String CUSTOM_PROPERTY_MIMETYPE = "mimetype";
  public static final String CUSTOM_PROPERTY_MEDIALOG = "medialog";
  public static final String CUSTOM_PROPERTY_RECOMPRESSION = "recompression";

  public static final long THRESHOLD_RATE = 1L * 1000L * 1000L;  // mean bytes per second
  
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
          + "          <d:prop><m:" + CUSTOM_PROPERTY_MIMETYPE + "/></d:prop>"
          + "          <d:literal>video/mp4</d:literal>"
          + "        </d:eq>"
          + "        <d:gt> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_MEDIADATARATE + "/></d:prop>"
          + "          <d:literal>" + THRESHOLD_RATE + "</d:literal>"
          + "        </d:gt>"
          + "        <d:eq> "
          + "          <d:prop><m:" + CUSTOM_PROPERTY_RECOMPRESSION + "/></d:prop>"
          + "          <d:literal>-1</d:literal>"
          + "        </d:eq>"
          + "      </d:and>"
          + "    </d:where>"
          + "  </d:basicsearch>"
          + "</d:searchrequest>";

  
  String query;
  String queuequery;
  String action;

  transient Calendar calends;
  transient PropertyDefinition propdefetag, propdefrate, propdefduration,
          propdefmimetype, propdefmedialog, propdefrecompression;
  transient ArrayList<String> list;
  transient Pattern filespattern;

  public AnalyseVideoTask(String servername, String action, String regex )
  {
    this.action = action;
    filespattern = Pattern.compile( regex ); // "/courses/\\d+-21\\d\\d/." );
    query = VIDEO_SEARCH_DASL;
    query = query.replace("{href}", "https://" + servername + "/bbcswebdav/");
    queuequery = VIDEO_SEARCH_QUEUE_DASL;
    queuequery = queuequery.replace("{href}", "https://" + servername + "/bbcswebdav/");
  }

  @Override
  public void doTask() throws InterruptedException
  {
    int analyses = 0;
    list = new ArrayList<>();
    calends = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    Path logfile = bbmonitor.logbase.resolve("videoanalysis-" + bbmonitor.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");

    try (PrintWriter log = new PrintWriter(new FileWriter(logfile.toFile()));)
    {
      bbmonitor.logger.info("Analyse video files (" + action + ")process started. May take many minutes. ");
      prepare(log);
      long start = System.currentTimeMillis();

      boolean recompress = "recompress".equals(action);
      if (!findVideoFiles(log, recompress?queuequery:query, filespattern))
      {
        bbmonitor.logger.info("Unable to build list of video files. ");
      } else
      {
        bbmonitor.logger.info("Completed list of video files. Size = " + list.size());
        for (String id : list)
        {
          if (Thread.interrupted())
            throw new InterruptedException();
          
          VideoCloneTarget[] targets = this.getCloneTargets( id, filespattern );
          
          if ("analyse".equals(action))
          {
            if ( !processOne(log, id) )
            { 
              bbmonitor.logger.error("Analysis failed so halting task." );
              return;
            }
          }
          if ("clear".equals(action))
            clearOne(log, id);
          if ("list".equals(action))
            listOne(log, id);
          if ( recompress )
            recompressOne(log, id);

          log.flush();
          analyses++;
//          if ( analyses > 1000)
//          {
//            bbmonitor.logger.info("Halting because reached file count limit. ");
//            break;
//          }
          long now = System.currentTimeMillis();
          if ((now - start) > (1L * 60L * 60L * 1000L))
          {
            bbmonitor.logger.info("Halting because reached elapsed time limit. ");
            break;
          }
        }
      }

      long end = System.currentTimeMillis();
      float elapsed = 0.001f * (float) (end - start);
      bbmonitor.logger.info("Analyse video file process ended after " + elapsed + " seconds. ");
    } catch (XythosException | IOException ex)
    {
      bbmonitor.logger.error("Analyse video file process failed ", ex );
    }
  }

  void prepare(PrintWriter log) throws XythosException
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdmin("VideoAnalysis");

      propdefetag = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_ANALYSEDETAG, context);
      if (propdefetag == null)
      {
        propdefetag = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE,
                CUSTOM_PROPERTY_ANALYSEDETAG,
                PropertyDefinition.DATATYPE_SHORT_STRING,
                false, // not versioned 
                true, // readable
                true, // writable
                false, // not caseinsensitive
                false, // not protected
                false, // not full text indexed
                "The etag value when the media metadata was last analysed.");
      }
      propdefmimetype = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MIMETYPE, context);
      if (propdefmimetype == null)
      {
        propdefmimetype = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE,
                CUSTOM_PROPERTY_MIMETYPE,
                PropertyDefinition.DATATYPE_SHORT_STRING,
                false, // not versioned 
                true, // readable
                true, // writable
                false, // not caseinsensitive
                false, // not protected
                false, // not full text indexed
                "The mimetype determined from metadata in the file.");
      }
      propdefmimetype = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MIMETYPE, context);
      if (propdefmedialog == null)
      {
        propdefmedialog = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE,
                CUSTOM_PROPERTY_MEDIALOG,
                PropertyDefinition.DATATYPE_STRING,
                false, // not versioned 
                true, // readable
                true, // writable
                false, // not caseinsensitive
                false, // not protected
                false, // not full text indexed
                "Logging text from the media analysis process.");
      }
      propdefrate = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADATARATE, context);
      if (propdefrate == null)
      {
        propdefrate = PropertyDefinitionManager.createIndexedLongPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADATARATE, true, true, false, false, "Average data rate of media in bytes per second.", context);
      }
      propdefduration = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADURATION, context);
      if (propdefduration == null)
      {
        propdefduration = PropertyDefinitionManager.createIndexedLongPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_MEDIADURATION, true, true, false, false, "Media duration in seconds.", context);
      }
      
      propdefrecompression = PropertyDefinitionManager.findPropertyDefinition(CUSTOM_PROPERTIES_NAMESPACE, CUSTOM_PROPERTY_RECOMPRESSION, context);
      if (propdefrecompression == null)
      {
        bbmonitor.logger.info( "No recompression dav property yet." );
        propdefrecompression = PropertyDefinitionManager.createPropertyDefinitionSafe(
                CUSTOM_PROPERTIES_NAMESPACE,
                CUSTOM_PROPERTY_RECOMPRESSION,
                PropertyDefinition.DATATYPE_SHORT_STRING,
                false, // not versioned 
                true, // readable
                true, // writable
                false, // not caseinsensitive
                false, // not protected
                false, // not full text indexed
                "Recompression status.");
      }
      else
      {
        bbmonitor.logger.info( "recompression dav property data type = " + propdefrecompression.getDatatype() );
      }
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
        bbmonitor.logger.error("Error occured trying to commit xythos context.", ex);
      }
    }
  }


  boolean findVideoFiles(PrintWriter log, String query, Pattern filepathpattern)
  {
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
          continue;
        com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
        // Ignore any files that have or will have versions
        if ( f.isRevisionable() || f.getFileVersionIDs().length > 1 )
          continue;
        
        //bbmonitor.logger.info( "Found video file " + fse.getName() );
        
        if (filepathpattern == null || filepathpattern.matcher(fse.getName()).matches())
          list.add(fse.getEntryID());
      }
    } catch (Throwable th)
    {
      bbmonitor.logger.error("Error occured while finding list of video files.", th);
      return false; // error so stop processing
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
    return true;
  }

  void clearOne(PrintWriter log, String id)
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

      Property aetag = f.getProperty(propdefetag, true, context);
      if (aetag != null)
      {
        f.deleteProperty(aetag, false, context);
      }
    } catch (Throwable th)
    {
      bbmonitor.logger.error("Error occured while clearing etag property on video files.", th);
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

  boolean processOne(PrintWriter log, String id)
  {
    Context context = null;
    try
    {
      context = AdminUtil.getContextForAdminUI("VideoAnalysis" + System.currentTimeMillis());
      FileSystemEntry fse = FileSystem.findEntryFromEntryID(id, false, context);
      if (fse == null)
        return true;
      if (!(fse instanceof com.xythos.fileSystem.File))
        return true;
      com.xythos.fileSystem.File f = (com.xythos.fileSystem.File) fse;
      Revision r = f.getLatestRevision();
      bbmonitor.logger.info("Found video file " + fse.getName());

      VideoSearchResult vsr = new VideoSearchResult();
      vsr.etag = r.getETagValue();
      Property aetag = f.getProperty(propdefetag, true, context);
      if (aetag != null)
      {
        vsr.analysedetag = aetag.getValue();
        bbmonitor.logger.info("Comparing " + vsr.etag + " with " + vsr.analysedetag);
        if (vsr.analysedetag.equals(vsr.etag))
        {
          bbmonitor.logger.info("Analysis is up to date.");
          return true;
        }
      }

      vsr.analysedetag = vsr.etag;
      vsr.fileid = f.getID();
      vsr.blobid = r.getBlobID();
      vsr.path = f.getName();
      vsr.size = r.getSize();
      vsr.recordedmimetype = f.getFileMimeType();
      analyseVideoFile(vsr, r);

      if (vsr.detectedmimetype != null)
      {
        Property p = f.getProperty(propdefmimetype, true, context);
        if (p != null)
        {
          f.deleteProperty(p, false, context);
        }
        f.addShortStringProperty(propdefmimetype, vsr.detectedmimetype, false, context);
      }

      if (vsr.duration >= 0)
      {
        Property p = f.getProperty(propdefduration, true, context);
        if (p != null)
        {
          f.deleteProperty(p, false, context);
        }
        f.addLongProperty(propdefduration, vsr.duration, false, context);
      }

      if (vsr.duration > 0 && vsr.size > 0)
      {
        vsr.datarate = vsr.size / vsr.duration;
        Property p = f.getProperty(propdefrate, true, context);
        if (p != null)
        {
          f.deleteProperty(p, false, context);
        }
        f.addLongProperty(propdefrate, vsr.datarate, false, context);
      }

      if (vsr.medialog != null)
      {
        Property p = f.getProperty(propdefmedialog, true, context);
        if (p != null)
        {
          f.deleteProperty(p, false, context);
        }
        f.addStringProperty(propdefmedialog, vsr.medialog, false, context);
      }

      if (true || vsr.analysed)
      {
        log.append(vsr.toString());
        bbmonitor.logger.info(vsr.medialog);
      }

      Property p = f.getProperty( propdefrecompression, true, context );
      if ( p == null )
        f.addStringProperty(propdefrecompression, "-1", false, context);
      
      // finish off by marking the file as analysed
      if (aetag != null)
      {
        f.deleteProperty(aetag, false, context);
      }
      f.addShortStringProperty(propdefetag, vsr.etag, false, context);
      
    } catch (Throwable th)
    {
      bbmonitor.logger.error("Error occured while running analysis of video files.", th);
      return false; // error so stop processing
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
        bbmonitor.logger.error("Error occured while running analysis of video files.", ex);
        return false;
      }
    }
    return true; // there might be more data to process...
  }

  void recompressOne(PrintWriter log, String id)
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
      Revision r = f.getLatestRevision();
      bbmonitor.logger.info("Found video file to recompress: " + fse.getName());
      bbmonitor.logger.info("File ID: " + f.getID() );
      bbmonitor.logger.info("Blob ID: " + r.getBlobID() );
      bbmonitor.logger.info("File docstore name: " + f.getDocstoreName() );
      bbmonitor.logger.info("Revision storage name: " + r.getStorageName() );
      
      Revision[] blobclones = Revision.findAssociatedRevisions( r.getBlobID(), context, f.getDocstoreName() );
      
      String etag = r.getETagValue();
      Property aetag = f.getProperty(propdefetag, true, context);
      if (aetag != null)
      {
        String analysedetag = aetag.getValue();
        bbmonitor.logger.info( "Comparing " + etag + " with " + analysedetag );
        if ( !analysedetag.equals( etag ) )
        {
          bbmonitor.logger.info( "Video file changed since it was last analysed so can't recompress now." );
          return;
        }
      }
      
      
      Property p = f.getProperty( propdefrecompression, true, context);
      if (p == null)
      {
        bbmonitor.logger.info( "File has no recompression property set." );
        return;
      }        
      if ( !"-1".equals( p.getValue() ) )
      {
        bbmonitor.logger.info( "Recompression  is not -1 so has already been recompressed." );
        return;
      }        
      p = f.getProperty( propdefrate, true, context );
      if (p == null)
      {
        bbmonitor.logger.info( "File has no data rate set." );
        return;
      }        
      long rate = Long.parseLong( p.getValue() );
      bbmonitor.logger.info( "Data rate = " + rate );
      if ( rate < THRESHOLD_RATE )
      {
        bbmonitor.logger.info( "File is already compressed enough." );
        return;
      }

      bbmonitor.logger.info( "Ready to compress." );
    }
    catch (Throwable th)
    {
      bbmonitor.logger.error("Error occured while running analysis of video files.", th);
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
        bbmonitor.logger.error("Error occured while running analysis of video files.", ex);
        return;
      }
    }
    return; // there might be more data to process...
  }
  
  void listOne(PrintWriter log, String id)
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
      Revision r = f.getLatestRevision();

      VideoSearchResult vsr = new VideoSearchResult();
      vsr.etag = r.getETagValue();
      Property aetag = f.getProperty(propdefetag, true, context);
      if (aetag != null)
      {
        vsr.analysedetag = aetag.getValue();
      }
      vsr.blobid = r.getBlobID();
      vsr.path = f.getName();
      vsr.size = r.getSize();
      vsr.recordedmimetype = f.getFileMimeType();

      Property p = f.getProperty(propdefduration, true, context);
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
      p = f.getProperty(propdefrate, true, context);
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

      p = f.getProperty(propdefmimetype, true, context);
      if (p != null)
      {
        vsr.detectedmimetype = p.getValue();
      }

//        p = f.getProperty(propdefmedialog, true, context);
//        if ( p != null )
//          bbmonitor.logger.info( p.getValue() );
      log.append(vsr.toString());
    } catch (Throwable th)
    {
      bbmonitor.logger.error("Error occured while running analysis of video files.", th);
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
        bbmonitor.logger.error("Error occured while running analysis of video files.", ex);
        return;
      }
    }
    return; // there might be more data to process...
  }

  boolean analyseVideoFile(VideoSearchResult vsr, Revision r)
  {
    //bbmonitor.logger.info("Heap free memory = " + Runtime.getRuntime().freeMemory());

    try (XythosAdapterChannel channel = new XythosAdapterChannel(r);
            InputStream in = Channels.newInputStream(channel);)
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
      String propermime = ftdirectory.getString(FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE);
      vsr.detectedmimetype = propermime;

      if ("video/quicktime".equals(vsr.detectedmimetype))
      {
        QuickTimeDirectory qt4d = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
        if (qt4d == null)
        {
          bbmonitor.logger.error("File has no quicktime metadata.");
        } else
        {
          try
          {
            vsr.duration = qt4d.getInt(QuickTimeDirectory.TAG_DURATION_SECONDS);
          } catch (MetadataException ex)
          {
            bbmonitor.logger.error("Error attempting to read video metadata.", ex);
          }
        }
      } else if ("video/mp4".equals(vsr.detectedmimetype))
      {
        Mp4Directory mp4d = metadata.getFirstDirectoryOfType(Mp4Directory.class);
        if (mp4d == null)
        {
          bbmonitor.logger.error("File has no mp4 metadata.");
        } else
        {
          try
          {
            vsr.duration = mp4d.getInt(Mp4Directory.TAG_DURATION_SECONDS);
          } catch (MetadataException ex)
          {
            bbmonitor.logger.error("Error attempting to read video metadata.", ex);
          }
        }
      } else if ("video/vnd.avi".equals(vsr.detectedmimetype))
      {
        AviDirectory avi4d = metadata.getFirstDirectoryOfType(AviDirectory.class);
        if (avi4d == null)
        {
          bbmonitor.logger.error("File has no avi metadata.");
        } else
        {
          try
          {
            vsr.duration = avi4d.getInt(AviDirectory.TAG_DURATION) / 1000000;
          } catch (MetadataException ex)
          {
            bbmonitor.logger.error("Error attempting to read video metadata.", ex);
          }
        }
      } else
      {
        message.append("Unrecognised mime type.\n");
      }

      for (com.drew.metadata.Directory directory : metadata.getDirectories())
      {
        for (com.drew.metadata.Tag tag : directory.getTags())
        {
          message.append( tag.toString().replace( "\u0000", "[0x00]" ) );
          message.append("\n");
        }
      }
      vsr.medialog = message.toString();
      //bbmonitor.logger.debug(vsr.medialog);
    } catch (Exception ex)
    {
      bbmonitor.logger.error("Problem reading metadata in media file.", ex);
      return false;
    }
    
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
      bbmonitor.logger.error( "getCloneTargets()", ex );
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
        bbmonitor.logger.error( "getCloneTargets() unable to commit context.", ex );
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
      if ( f_other.getFileVersionIDs().length > 1 )
        continue;
      VideoCloneTarget target = new VideoCloneTarget( f_other );
      bbmonitor.logger.info("  Associated Revision: " + target.fileid + " " + target.name + " " + target.etag );
      if ( filepathpattern == null || filepathpattern.matcher( target.name ).matches() )
        list.add( target );
    }
    return list.toArray( new VideoCloneTarget[ list.size() ] );
  }
  
  class VideoCloneTarget
  {
    String name;
    long fileid;
    String etag;

    public VideoCloneTarget( com.xythos.fileSystem.File f ) throws StorageServerException
    {
      this.name   = f.getName();
      this.fileid = f.getID();
      this.etag   = f.getETag();
    }
    
  }
}
