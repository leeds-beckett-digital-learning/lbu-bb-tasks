/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import com.xythos.common.InternalException;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.common.dbConnect.JDBCConnection;
import com.xythos.common.dbConnect.JDBCConnectionPool;
import com.xythos.security.ContextImpl;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystemEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.leedsbeckett.bbtasks.BlobSearchResult;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 *
 * @author jon
 */
public class XythosAnalyseDeletedAutoArchiveTask extends BaseTask
{
  String vsname;
  int y, m, d, y2, m2, d2;

  public XythosAnalyseDeletedAutoArchiveTask( String vsname, int y, int m, int d, int y2, int m2, int d2 )
  {
    this.vsname = vsname;
    this.y = y;
    this.m = m;
    this.d = d;
    this.y2 = y2;
    this.m2 = m2;
    this.d2 = d2;
  }

  @Override
  public void doTask() throws InterruptedException
  {
    VirtualServer vs = VirtualServer.find( vsname );
    ArrayList<BlobSearchResult> list = new ArrayList<>();
    Path logfile = webappcore.logbase.resolve( "deletedautoarchiveanalysis-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".txt" );
    long tiirunningtotal = 0L;
    long csrunningtotal = 0L;

    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( "Starting to analyse deleted autoarchives. This may take many minutes." );
    }
    catch (IOException ex)
    {
      webappcore.logger.error( "Error writing to output file.", ex);
    }

    webappcore.logger.info( "Analyse deleted autoarchives process started. May take many minutes. " ); 
    long start = System.currentTimeMillis();

    Context context = null;
    FileSystemEntry entry = null;
    StringBuilder message = new StringBuilder();

    try
    {
      String[] l_fileSystems = LocalXythosUtils.getFileSystems();
      for (int i = 0; i < l_fileSystems.length; ++i)
      {
        if ( Thread.interrupted() ) throw new InterruptedException();
        // modelled after com.xythos.util.ProcessDocstoreThread.performAction()
        final JDBCConnectionPool l_pool = JDBCConnectionPool.getJDBCConnectionPool(l_fileSystems[i]);
        if (!l_pool.getType().equals("DOCUMENT_STORE")) {
            continue;
        }
        ContextImpl l_adminContext = (ContextImpl)AdminUtil.getContextForAdmin("AnalyseAutoArchiveThread:285");
        l_adminContext.setOperationTypeIfNotSet(14);
        JDBCConnection l_dbcon = null;
        try {
            l_dbcon = l_adminContext.getDBConnection(l_pool.getPoolID());
            l_dbcon.setNeedToCommit(true);

            LocalXythosUtils.findBinaryObjects( l_dbcon, list, y, m, d, y2, m2, d2 );

            for ( int j=0; j<list.size(); j++ )
            {
              if ( Thread.interrupted() ) throw new InterruptedException();
              webappcore.logger.info( "Working on entry " + j + " of " + list.size() );
              BlobSearchResult bsr = list.get(j);
              LocalXythosUtils.getBinaryObject( l_dbcon, bsr );                  
            }

            l_adminContext.commitContext();
            l_adminContext = null;
            l_dbcon = null;
        }    
        finally
        {
          if (l_adminContext != null)
          {
              l_adminContext.rollbackContext();
              l_adminContext = null;
          }
        }          
      }
    }
    catch (SQLException | IOException | XythosException ex)
    {
      webappcore.logger.error( "Error processing Xythos files.", ex);
    }
    
    int n=0;
    for ( BlobSearchResult i : list )
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      if ( i.analysed )
      {
        n++;
        message.append( "blob," + i.id + "," + i.refcount + "," + i.size + "," + i.storagefilename + "," + i.tempstoragefilename + "," + (i.iszip?"zip":"other") + "," + i.turnitinusage + "," + i.csfilesusage + "," );
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
        tiirunningtotal += i.turnitinusage;
        csrunningtotal  += i.csfilesusage;
        message.append( "\n" );
      }
    }
    message.append( "Blob count = " + list.size() + " analysed zips = " + n + "\n" );

    message.append( "Found " + tiirunningtotal + " bytes of Turnitin uploads in the deleted autoarchives." );
    message.append( "Found " + csrunningtotal + " bytes of 'legacy' content uploads in the deleted autoarchives." );

    long end = System.currentTimeMillis();
    float elapsed = 0.001f * (float)(end-start);
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
    {
      log.println( message );
      log.println( "Analyse deleted autoarchives process ended after " + elapsed + " seconds. "      );
    } catch (IOException ex)
    {
      webappcore.logger.error( "Error writing to output file.", ex);
    }
    webappcore.logger.error( "Done attempting to analyse deleted autoarchives." );
  }


}
