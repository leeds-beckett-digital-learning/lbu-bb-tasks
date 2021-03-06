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

import java.net.URL;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    
    String[] classnames = 
    {
      "com.xythos.webui.docflow.TemplateList",
      "com.xythos.webview.XythosAction",
      "org.apache.lucene.document.Fieldable",
      "org.elasticsearch.client.RestClientBuilder",
      "org.mozilla.intl.chardet.nsDetector",
      "org.apache.poi.extractor.POITextExtractor",
      "org.postgresql.fastpath.Fastpath"
    };
    
    for ( String name : classnames )
    {
      try
      {
        Class c = Class.forName( name );
        URL location = c.getResource('/' + c.getName().replace('.', '/') + ".class");    
        webappcore.logger.info( location.toExternalForm()  );
      }
      catch (ClassNotFoundException ex) { webappcore.logger.error( "Class not found ", ex ); }
    }
    
    
    Random r = new Random();
    double sum  = 0.0;
    long i, n=0;
    for ( i=1; i<=(1024L*1024L*1024L); i++ )
    {
      sum += r.nextGaussian();
      n++;
      if ( Thread.interrupted() )
      {
        webappcore.logger.info( "Demo task interrupted. Stopping early." );
        break;
      }
    }
    double mean = sum / (double)n;
    long now = System.currentTimeMillis();
    webappcore.logger.info( "Mean of " + i + " random numbers = " + mean );
    webappcore.logger.info( "Demo task completed in " + (now-started) + " milliseconds." );
  }
}
