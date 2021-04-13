/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Stream;
import uk.ac.leedsbeckett.bbcswebdavmonitor.BBMonitor;

/**
 *
 * @author jon
 */
public class PruneThread extends Thread
{
  BBMonitor bbmonitor;
  Path logfile;
  
  public PruneThread( BBMonitor bbmonitor, Path logfile )
  {
    this.bbmonitor = bbmonitor;
    this.logfile = logfile;
    bbmonitor.setCurrentTask( this );
  }

  @Override
  public void run()
  {
    try
    {
      doIt();
    }
    finally
    {
      bbmonitor.setCurrentTask( null );
    }
  }
  
  private void doIt()
  {
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to prune turnitin files in legacy file system. This may take many minutes." );
    } catch (IOException ex)
    {
      bbmonitor.logger.error( "Error attempting to prune turnitin files.", ex);
      return;
    }

    long start = System.currentTimeMillis();
    bbmonitor.logger.info( "Turn It In pruning process started." );
    int filesmoved = 0;

    Path coursebase = bbmonitor.getVirtualserverbase().resolve( "courses/1" );
    BigInteger totalgood = BigInteger.ZERO;
    BigInteger totalbad = BigInteger.ZERO;

    // Prep...
    Path tempstorepath    = bbmonitor.getVirtualserverbase().resolve( "courses_TEMPORARY_COPIES" );
    Path coursetargetpath = tempstorepath.resolve( "1" );
    try
    {
      if ( !Files.exists( tempstorepath ) )
        Files.createDirectory( tempstorepath    );
      if ( !Files.exists( coursetargetpath ) )
        Files.createDirectory( coursetargetpath );

      ArrayList<Path> coursepaths = new ArrayList<>();
      ArrayList<Path> filepaths = new ArrayList<>();
      // get a list in a way that autocloses the directory
      try ( Stream<Path> stream = Files.list(coursebase); )
      {
        stream.forEach( new Consumer<Path>(){public void accept( Path f ) {coursepaths.add(f);}});
      }

      for ( int i=0; i<coursepaths.size(); i++ )
      {     
        Path f = coursepaths.get(i);
        filepaths.clear();
        String courseid = f.getFileName().toString();
        Path uploadssource = f.resolve("ppg").resolve("BB_Direct").resolve("Uploads");
        bbmonitor.logger.info( "Working on: " + i + " of " + coursepaths.size() + " = " + uploadssource.toString() );
        if ( Files.exists( uploadssource ) && Files.isDirectory( uploadssource ) )
        {
          Path target = tempstorepath.resolve( courseid );
          bbmonitor.logger.info( "Target directory: " + target.toString() );
          if ( !Files.exists(target) )
            Files.createDirectory( target );
          // get a list in a way that autocloses the directory
          try ( Stream<Path> stream = Files.list(uploadssource); )
          {
            stream.forEach( new Consumer<Path>(){public void accept( Path f ) {filepaths.add(f);}});
          }

          for ( Path uploadedfile : filepaths )
          {
            if ( Files.isRegularFile( uploadedfile ) )
            {
              // changing criteria for deletion - file was created more than 1 hour ago
              Instant onehourago = Instant.now().minus( 1, ChronoUnit.HOURS );
              Instant mod = Files.getLastModifiedTime( f ).toInstant();
              bbmonitor.logger.info( "Processing : " + uploadedfile.toString() + "   " + mod );
              if ( mod.isBefore( onehourago ) )
              {
                String name = uploadedfile.getFileName().toString();
                bbmonitor.logger.info( "Moving " + name );
                Path targetfile = target.resolve( name );
                //bbmonitor.logger.info( "To here: " + targetfile.toString() );
                if ( uploadedfile.getFileSystem() == targetfile.getFileSystem() )
                {
                  //bbmonitor.logger.info( "Same file system." );
                  if ( Files.exists( targetfile ) )
                  {
                    bbmonitor.logger.info( "Target already exists." + targetfile.toString() );
                    if ( !(Files.size( uploadedfile ) != Files.size(targetfile)) )
                      bbmonitor.logger.info( "Target IS THE WRONG SIZE." );
                  }
                  else
                  {
                    Files.move( uploadedfile, targetfile );
                    filesmoved++;
                  }
                }
              }
            }
          }
        }
      }
    }
    catch ( Exception ex )
    {
      bbmonitor.logger.error( "Error attempting to prune turnitin files." );
      bbmonitor.logger.error(ex);
    }

    long end = System.currentTimeMillis();
    float elapsed = 0.001f * (float)(end-start);
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Turn It In pruning process ended after " + elapsed + " seconds. " + filesmoved + " files moved."       );
    }
    catch ( Exception ex )
    {
      bbmonitor.logger.error( "Error attempting to prune turnitin files.", ex);
    }
  }  
}
