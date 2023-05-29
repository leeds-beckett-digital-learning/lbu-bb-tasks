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

import blackboard.data.ValidationException;
import blackboard.data.content.Content;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.content.ContentDbLoader;
import blackboard.persist.content.ContentDbPersister;
import blackboard.persist.course.CourseMembershipDbPersister;
import blackboard.platform.extension.Extension;
import blackboard.platform.extension.ExtensionPoint;
import blackboard.platform.extension.impl.ExtensionProxyFactory;
import blackboard.platform.extension.impl.SingletonExtensionImpl;
import blackboard.platform.extension.service.ExtensionRegistry;
import blackboard.platform.extension.service.ExtensionRegistryFactory;
import blackboard.platform.extension.service.impl.ExtensionRegistryService;
import blackboard.platform.rest.content.ContentHandlerProvider;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.ClassUtils;

/**
 *
 * @author jon
 */
public class DemoTask extends BaseTask
{

  @Override
  public void doTask() throws InterruptedException
  {
    webappcore.logger.info( "Demo task started." );
    long started = System.currentTimeMillis();

    Path logfile = webappcore.logbase.resolve( "turnitinunavailable-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "contentid" );
            
      ContentDbLoader loader;
      ContentDbPersister persister;
      try
      {
        loader    = ContentDbLoader.Default.getInstance();
        persister = ContentDbPersister.Default.getInstance();    
      }
      catch ( PersistenceException ex )
      {
        debuglogger.error( "Aborted because can't load ContentDBLoader." );
        return;
      }

      Path contentidfile = webappcore.pluginbase.resolve( "turnitin_id_list.txt" );
      String line;
      try (  BufferedReader reader = new BufferedReader( new FileReader( contentidfile.toFile() ) ) )
      {
        while ( ( line = reader.readLine() ) != null )
        {
          if ( Thread.interrupted() )
          {
            throw new InterruptedException();
          }
          debuglogger.error( "Working on " + line );

          try
          {
            Id contentId = Id.generateId( Content.DATA_TYPE, line );
            Content content = loader.loadById( contentId );
            debuglogger.error( "  Found     " + content.getTitle() );
            //debuglogger.error( "  Handler   " + content.getContentHandler() );
            //debuglogger.error( "  Available " + content.getIsAvailable() );

            // Double check this is the right kind of content
            if ( "resource/x-turnitintool-assignment".equals( content.getContentHandler() ) 
                    && content.getIsAvailable() )
            {
              debuglogger.error( "    =====  Setting available to No " );
              content.setIsAvailable( false );
              persister.persist( content );
              log.println( contentId.toExternalString() );
            }
          }
          catch ( KeyNotFoundException ex )
          {
            debuglogger.error( "  Content not found - skipping." );          
          }
          catch ( PersistenceException ex )
          {
            debuglogger.error( "  Unable to save changes.", ex );
            webappcore.logger.error( "Failed ", ex );
            break;
          }
          catch ( ValidationException ex )
          {
            debuglogger.error( "  Content not saved - bailing out", ex );
            webappcore.logger.error( "Aborting", ex );
            break;
          }
        }
      }
      catch ( IOException ex )
      {
        debuglogger.error( "Error reading email job file. ", ex );
      }

      long now = System.currentTimeMillis();
      webappcore.logger.info( "Demo task completed in " + ( now - started ) + " milliseconds." );

    }
    catch (IOException ex)
    {
      webappcore.logger.error( "Error writing to task output file.", ex );
      return;
    }

    
  }

  public void doTaskX() throws InterruptedException
  {
    webappcore.logger.info( "Demo task started." );
    long started = System.currentTimeMillis();

    ExtensionRegistry er = ExtensionRegistryFactory.getInstance();
    debuglogger.info( "ExtensionRegistry class " + er.getClass() );
    if ( er instanceof ExtensionRegistryService )
    {
      ExtensionRegistryService ers = (ExtensionRegistryService) er;
      ExtensionPoint ep = ers.getExtensionPoint( ContentHandlerProvider.EXTENSION_POINT );

      for ( Extension ext : ep.getExtensions() )
      {
        debuglogger.info( "Extension " + ext.getClass() );
        for ( Class c : ClassUtils.getAllInterfaces( ext.getClass() ) )
        {
          debuglogger.info( "    implements " + c );
        }
        if ( ext instanceof ContentHandlerProvider )
        {
          debuglogger.info( "    Is a ContentHandlerProvider" );
        }
        if ( ext instanceof SingletonExtensionImpl )
        {
          SingletonExtensionImpl sexti = (SingletonExtensionImpl) ext;
          debuglogger.info( "    " + sexti.getUniqueIdentifier() );
          debuglogger.info( "    " + sexti.getSourceInfo().getVendorId() );
          debuglogger.info( "    " + sexti.getSourceInfo().getContextPath() );
          debuglogger.info( "    " + sexti.getSourceInfo().getHandle() );
        }
      }
    }

    debuglogger.info( "ContentHandlerProvider factory class " + ContentHandlerProvider.Factory.getClass() );
    Collection<ContentHandlerProvider> instances = ContentHandlerProvider.Factory.getInstances();

    if ( instances == null )
    {
      debuglogger.info( "instances == null" );
    }
    else
    {
      for ( ContentHandlerProvider i : instances )
      {
        debuglogger.info( "ContentHandlerProvider implemented by " + i.getClass() );
        for ( Class c : ClassUtils.getAllInterfaces( i.getClass() ) )
        {
          debuglogger.info( "    implements " + c );
        }
        if ( Proxy.isProxyClass( i.getClass() ) )
        {
          debuglogger.info( "  Invocation handler = " + Proxy.getInvocationHandler( i ) );
        }
        if ( i.supportsContentHandler( "resource/x-bb-folder" ) )
        {
          debuglogger.info( "  Supports folder" );
        }
        if ( i.supportsContentHandler( "resource/x-turnitintool-assignment" ) )
        {
          debuglogger.info( "  Supports TII assignment" );
        }
      }
    }

//    String[] classnames = 
//    {
//      "com.xythos.webui.docflow.TemplateList",
//      "com.xythos.webview.XythosAction",
//      "org.apache.lucene.document.Fieldable",
//      "org.elasticsearch.client.RestClientBuilder",
//      "org.mozilla.intl.chardet.nsDetector",
//      "org.apache.poi.extractor.POITextExtractor",
//      "org.postgresql.fastpath.Fastpath"
//    };
//    
//    for ( String name : classnames )
//    {
//      try
//      {
//        Class c = Class.forName( name );
//        URL location = c.getResource('/' + c.getName().replace('.', '/') + ".class");    
//        webappcore.logger.info( location.toExternalForm()  );
//      }
//      catch (ClassNotFoundException ex) { webappcore.logger.error( "Class not found ", ex ); }
//    }
//    Random r = new Random();
//    double sum  = 0.0;
//    long i, n=0;
//    for ( i=1; i<=(1024L*1024L*1024L); i++ )
//    {
//      sum += r.nextGaussian();
//      n++;
//      if ( Thread.interrupted() )
//      {
//        webappcore.logger.info( "Demo task interrupted. Stopping early." );
//        break;
//      }
//    }
//    double mean = sum / (double)n;
    long now = System.currentTimeMillis();
//    webappcore.logger.info( "Mean of " + i + " random numbers = " + mean );
    webappcore.logger.info( "Demo task completed in " + ( now - started ) + " milliseconds." );
  }
}
