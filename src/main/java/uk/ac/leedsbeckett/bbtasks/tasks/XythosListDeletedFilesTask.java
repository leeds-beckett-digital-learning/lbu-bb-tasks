/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import com.xythos.common.InternalException;
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
import java.util.ArrayList;
import java.util.Date;
import uk.ac.leedsbeckett.bbtasks.BlobSearchResult;
import uk.ac.leedsbeckett.bbtasks.xythos.LocalXythosUtils;

/**
 *
 * @author jon
 */
public class XythosListDeletedFilesTask extends BaseTask
{
  @Override
  public void doTask() throws InterruptedException
  {
    ArrayList<BlobSearchResult> list = new ArrayList<>();
    Path logfile = webappcore.logbase.resolve( "deletedfiles-" + webappcore.dateformatforfilenames.format( new Date(System.currentTimeMillis() ) ) + ".csv" );

    webappcore.logger.info( "List started. May take many minutes. " ); 

    Context context = null;
    FileSystemEntry entry = null;
    StringBuilder message = new StringBuilder();
    try ( PrintWriter log = new PrintWriter( new FileWriter( logfile.toFile() ) ); )
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

            LocalXythosUtils.listBinaryObjects( l_dbcon, log );

            l_adminContext.commitContext();
            l_adminContext = null;
            l_dbcon = null;
        }
        catch ( SQLException | InternalException ex)
        {
          webappcore.logger.error( "Error while running task.", ex );
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
    catch (IOException | XythosException ex)
    {
      webappcore.logger.error( "Error while running task.", ex );
    }
      
  }
}
