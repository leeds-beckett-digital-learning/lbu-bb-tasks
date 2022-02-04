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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import uk.ac.leedsbeckett.bbtasks.WebAppCore;

/**
 *
 * @author jon
 */
public abstract class BaseTask implements Runnable
{
  @JsonIgnore
  WebAppCore webappcore = null;
  
  @JsonIgnore
  String name = "";

  @JsonIgnore
  Logger debuglogger;
  
  @JsonIgnore
  FileAppender logappender;
  
  @JsonIgnore
  public WebAppCore getWebAppCore()
  {
    return webappcore;
  }

  @JsonIgnore
  public void setWebAppCore( WebAppCore webappcore )
  {
    this.webappcore = webappcore;
  }

  @JsonIgnore
  public String getName()
  {
    return name;
  }  

  public void initLogger() throws IOException
  {
    Path coursebase = webappcore.virtualserverbase.resolve("courses/1/");
    Path debuglogfile = webappcore.logbase.resolve( "task-debug-log-" + name + ".txt" );
    String logfilename = debuglogfile.toString();
    debuglogger = LogManager.getLoggerRepository().getLogger( logfilename );
    debuglogger.setLevel( Level.INFO );
    logappender = new FileAppender( 
                          new PatternLayout( "%d{ISO8601} %-5p: %m%n" ), 
                          logfilename, 
                          true );
    debuglogger.removeAllAppenders();
    debuglogger.addAppender(logappender );
    debuglogger.info( "==========================================================" );
    debuglogger.info( "Log file has been opened." );
    debuglogger.info( "==========================================================" );        
  }
  
  public final void run()
  {
    try
    {
      name = webappcore.dateformatforfilenames.format( new Date( System.currentTimeMillis() ) );
      initLogger();
      debuglogger.info( "The webappcore logger is " + webappcore.logger.getName() );
      webappcore.logger.info( "Starting task - " + getClass() );
      doTask();
      webappcore.logger.info( "Completed task normally - " + getClass() );
    }
    catch ( InterruptedException ie )
    {
      webappcore.logger.info( "Task was interrupted - " + getClass() );
    }
    catch ( Throwable th )
    {
      webappcore.logger.error( "Task stopped abnormally " + getClass(), th );
    }
    logappender.close();
  }

  public abstract void doTask() throws InterruptedException;
}
