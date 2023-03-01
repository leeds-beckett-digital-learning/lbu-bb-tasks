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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author jon
 */
public class CommandTask extends BaseTask
{
  public String command;

  @JsonCreator
  public CommandTask( @JsonProperty("command") String command )
  {
    this.command = command;
  }

  public int runCommand( File launchfile, File executablefile, File logfile ) throws InterruptedException
  {
    Process p = null;
    try
    {
      ProcessBuilder pb = new ProcessBuilder( 
              launchfile.getAbsolutePath(), 
              executablefile.getAbsolutePath() );
      //pb.environment().put( "LD_LIBRARY_PATH", "." );
      pb.directory( launchfile.getParentFile() );
      pb.redirectErrorStream( true );
      pb.redirectOutput( ProcessBuilder.Redirect.appendTo( logfile ) );
      p = pb.start();
      while ( p.isAlive() )
      {
        debuglogger.debug( "external process still running." );
        p.waitFor( 10, TimeUnit.SECONDS );
      }
      debuglogger.info( "exit code = " + p.exitValue() );
      return p.exitValue();
    }
    catch ( InterruptedException ie )
    {
      if ( p != null )
      {
        p.destroy();
        debuglogger.error( "Cancelled process because this task was interrupted." );
      }
      throw ie;
    }
    catch ( Exception ex )
    {
      debuglogger.error( "Exception while trying to run " + executablefile.getAbsolutePath(), ex );
    }
    return -1;
  }  
  
  
  @Override
  public void doTask() throws InterruptedException
  {
    Path commandbase = webappcore.pluginbase.resolve( "command" );
    Path commandpath = commandbase.resolve( command );
    Path launchpath = commandbase.resolve( "launch" );
    Path commandoutputbase = commandbase.resolve( "output" );    
    Path commandoutput = commandoutputbase.resolve("out-" + webappcore.dateformatforfilenames.format(new Date(System.currentTimeMillis())) + ".txt");
    File commandfile, launchfile;
    
    try
    {
      if ( !Files.exists( commandbase ) )
        Files.createDirectory( commandbase );
      if ( !Files.exists( commandoutputbase ) )
        Files.createDirectory( commandoutputbase );
      if ( !Files.exists( launchpath ) )
      {
        debuglogger.error( "The launch file doesn't exist." );      
        return;
      }
      if ( !Files.exists( commandpath ) )
      {
        debuglogger.error( "The requested command doesn't exist." );      
        return;
      }
      launchfile = launchpath.toFile();
      commandfile = commandpath.toFile();
      debuglogger.error( "The launch file is " + commandfile.getAbsolutePath() );      
      if ( !launchfile.canExecute() )
      {
        debuglogger.info( "The launch file is not executable. Fixing that." );      
        launchfile.setExecutable( true );
      }
      debuglogger.error( "The requested command is " + commandfile.getAbsolutePath() );      
      if ( !commandfile.canExecute() )
      {
        debuglogger.info( "The requested command is not executable. Fixing that." );      
        commandfile.setExecutable( true );
      }
    }
    catch ( IOException ioe )
    {
      debuglogger.error( "Exception trying to create directories.", ioe);
    }
    
    runCommand( launchpath.toFile(), commandpath.toFile(), commandoutput.toFile() );
    debuglogger.info("End of legacy file system search.");
  }
}
