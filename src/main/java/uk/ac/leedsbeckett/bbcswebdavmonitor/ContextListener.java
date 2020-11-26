/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.data.user.User;
import blackboard.data.user.UserInfo;
import blackboard.persist.DataType;
import blackboard.persist.Id;
import blackboard.persist.user.UserDbLoader;
import blackboard.platform.intl.BbLocale;
import blackboard.platform.plugin.PlugInUtil;
import com.xythos.fileSystem.events.EventSubQueue;
import com.xythos.fileSystem.events.FileSystemEntryCreatedEventImpl;
import com.xythos.fileSystem.events.StorageServerEventBrokerImpl;
import com.xythos.fileSystem.events.StorageServerEventListener;
import com.xythos.security.api.Context;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemEntryCreatedEvent;
import com.xythos.storageServer.api.FileSystemEvent;
import com.xythos.storageServer.api.VetoEventException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;


/**
 *
 * @author jon
 */
@WebListener
public class ContextListener implements ServletContextListener, StorageServerEventListener
{
  public static Logger logger = null;
  public static Logger datalogger = null;
  RollingFileAppender datarfapp = null;

  private static AtomicInteger instancecount = new AtomicInteger(0);
  private static String staticid=null;
  private static StringBuilder bootstraplog = new StringBuilder();
  private static Class[] listensfor = {FileSystemEntryCreatedEventImpl.class};
  private static Properties defaultproperties = new Properties();
  private static BuildingBlockProperties configproperties = new BuildingBlockProperties();
  private static String logfolder;
  private static BbLocale locale = new BbLocale();
  private static String[] allowedpropertynames = 
  {
    "runlevel"
  };
  


  String instanceid;
  String buildingblockhandle;
  String buildingblockvid;
  ServerCoordinator servercoordinator;
  boolean monitoringxythos=false;
  String serverid;
  
  public ContextListener()
  {
    if ( staticid == null )
    {
      Random r = new Random();
      staticid = "ID_" + Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString(r.nextLong());
    }
    int count =  instancecount.incrementAndGet();
    instanceid = staticid + "_" + count;
    ContextListener.logToBuffer("ContextListener constructor." );    
    if ( count > 1 )
      ContextListener.logToBuffer("WHOOOOOAAAAAH. This constructor has been called more than once in this class loader!" );
  }
  
  
  @Override
  public void contextInitialized(ServletContextEvent sce)
  {
    ContextListener.logToBuffer("BB plugin init");

    try
    {
      serverid = InetAddress.getLocalHost().getHostName();
      ContextListener.logToBuffer( serverid );
    }
    catch (UnknownHostException ex)
    {
      ContextListener.logToBuffer( "Unable to find local IP address." );
      ContextListener.logToBuffer( ex );
    }
    
    if ( !initDefaultSettings( sce ) )
      return;
    
    if ( !loadSettings() )
      return;

    
    servercoordinator = new ServerCoordinator();
    if ( !servercoordinator.startInterserverMessaging( this, buildingblockvid + "_" + buildingblockhandle ) )
      return;
  }

  
  public boolean initDefaultSettings(ServletContextEvent sce)
  {
    String strfile = sce.getServletContext().getRealPath("WEB-INF/defaultsettings.properties" );
    ContextListener.logToBuffer("Expecting to find default properties here: " + strfile );
    File file = new File( strfile );
    if ( !file.exists() )
    {
      ContextListener.logToBuffer("It doesn't exist - cannot start." );
      return false;
    }
    
    try ( FileReader reader = new FileReader( file ) )
    {
      defaultproperties.load(reader);
    }
    catch (Exception ex)
    {
      logToBuffer( ex );
      return false;
    }
    
    buildingblockhandle = defaultproperties.getProperty("buildingblockhandle","");
    buildingblockvid = defaultproperties.getProperty("buildingblockvendorid","");
    if ( buildingblockhandle.length() == 0 || buildingblockvid.length() == 0 )
    {
      ContextListener.logToBuffer( "Cannot work out bb handle or vendor id so can't load configuration." );
      return false;      
    }
    
    return true;
  }
  
  public boolean loadSettings()
  {
    try
    {
      File strdir = PlugInUtil.getConfigDirectory(buildingblockvid, buildingblockhandle);
      ContextListener.logToBuffer( "Building block config directory is here: " + strdir );
      logfolder = strdir.getPath() + "/log/";
      ContextListener.logToBuffer( "Log folder here: " + logfolder );
      
      initLogging();
      
      File propsfile = new File( strdir, buildingblockhandle + ".properties" );
      ContextListener.logToBuffer("Config properties file is here: " + propsfile );
      if ( !propsfile.exists() )
      {
        ContextListener.logToBuffer( "Doesn't exist so creating it now." );
        propsfile.createNewFile();
      }
      ContextListener.logToBuffer( "Config properties file last modified: " + propsfile.lastModified() );
      try ( FileReader reader = new FileReader( propsfile ) )
      {
        configproperties.load(reader);
      }
      catch (Exception ex)
      {
        ContextListener.logToBuffer( ex );
        return false;
      }
      ContextListener.logToBuffer( "Loaded configuration." );      
    }
    catch ( Throwable th )
    {
      ContextListener.logToBuffer( "Failed to load configuration." );
      logToBuffer( th );
      return false;            
    }
    
    
    return true;
  }
  
  /**
   * Manually configure logging because we only know where to put
   * the logs at run time.
   * @param logfilefolder 
   */
  public void initLogging(  ) throws IOException
  {
    File logdir = new File( logfolder );
    if ( !logdir.exists() )
      logdir.mkdir();

    
    Logger rootlog = LogManager.getLoggerRepository().getRootLogger();
    if ( rootlog == null )
      ContextListener.logToBuffer( "No root log found." );
    else
      ContextListener.logToBuffer( "Root log: " + rootlog.getName() );
    
    logger = LogManager.getLoggerRepository().getLogger( ContextListener.class.getName() );
    logger.setLevel( Level.INFO );
    String logfilename =  logfolder + serverid + ".log";
    ContextListener.logToBuffer( logfilename );
    RollingFileAppender rfapp = 
        new RollingFileAppender( 
            new PatternLayout( "%d{ISO8601} %-5p: %m%n" ), 
            logfilename, 
            true );
    rfapp.setMaxBackupIndex( 20 );
    rfapp.setMaxFileSize( "100MB" );
    logger.removeAllAppenders();
    logger.addAppender( rfapp );
    
    datalogger = LogManager.getLoggerRepository().getLogger( ContextListener.class.getName() + "/datalogger" );
    datalogger.setLevel( Level.INFO );
    datalogger.removeAllAppenders();
  }
  
  
  @Override
  public void contextDestroyed(ServletContextEvent sce)
  {
    logger.info("BB plugin destroy");
    try
    {
      if ( servercoordinator != null )
        servercoordinator.stopInterserverMessaging();
      stopMonitoringXythos();
    }
    catch ( Throwable th )
    {
      logger.error( th );
    }
  }



  public void startMonitoringXythos()
  {
    if ( monitoringxythos )
      return;
    try
    {
      logger.info("Starting listening to Xythos." );
    
      String logfilename =  logfolder + "bigfiles.log";
      ContextListener.logToBuffer( logfilename );
      datarfapp = 
          new RollingFileAppender( 
              new PatternLayout( "%d{ISO8601},%m%n" ), 
              logfilename, 
              true );
      datarfapp.setMaxBackupIndex( 20 );
      datarfapp.setMaxFileSize( "100MB" );
      datalogger.removeAllAppenders();
      datalogger.addAppender( datarfapp );    

      StorageServerEventBrokerImpl.addAsyncListener(this);
      monitoringxythos = true;
    }
    catch ( Throwable th )
    {
      logger.error( th );
    }   
  }

  public void stopMonitoringXythos()
  {
    if ( !monitoringxythos )
      return;
    datalogger.removeAllAppenders();
    if ( datarfapp != null )
    {
      datarfapp.close();
      datarfapp = null;
    }
    logger.info( "Stopping listening to Xythos." );
    StorageServerEventBrokerImpl.removeAsyncListener( this );    
    monitoringxythos = false;
  }
  
  
  
  @Override
  public Class[] listensFor()
  {
    return listensfor;
  }

  @Override
  public void processEvent(Context cntxt, FileSystemEvent fse) throws Exception, VetoEventException
  {
    if ( !(fse instanceof FileSystemEntryCreatedEvent ) )
      return;
    
    try
    {
      FileSystemEntryCreatedEvent fsece = (FileSystemEntryCreatedEvent)fse;
      logger.debug( "BlackboardBackend - create entry event = " + fsece.getFileSystemEntryName() );
      logger.debug( "BlackboardBackend -           entry id = " + fsece.getEntryID()             );
      if ( fsece.getSize() < (1024*1024*100) )
        return;
      logger.info( "File over 100 Mbytes created: " + fsece.getFileSystemEntryName() + 
                   " Size = " + (fsece.getSize()/(1024*1024)) + "Mb  Owner = " + fsece.getOwnerPrincipalID());
      FileSystemEntry entry = FileSystem.findEntryFromEntryID( fsece.getEntryID(), false, cntxt );
      if ( entry != null )
      {
        String longid = entry.getCreatedByPrincipalID();
        if ( longid.startsWith( "BB:U:" ) )
        {
          String shortid = longid.substring( 5 );
          logger.info( "Created by " + longid + "  =  " + shortid );
          UserDbLoader userdbloader = UserDbLoader.Default.getInstance();
          User user = userdbloader.loadById( Id.toId( User.DATA_TYPE, shortid ) );
          logger.info( "User name of file creator: " + user.getUserName() );
          logger.info( "Email of file creator: "     + user.getEmailAddress() );
          logger.info( "Name of file creator: "      + user.formatName( locale, BbLocale.Name.DEFAULT ) );
          datalogger.info( 
                  fsece.getFileSystemEntryName() + "," +
                  (fsece.getSize()/(1024*1024))  + "," + 
                  user.getUserName()             + "," +
                  user.getEmailAddress()         + "," +
                  user.formatName( locale, BbLocale.Name.DEFAULT )  );
        }
      }
    }
    catch ( Exception e )
    {
      logger.error( "Exception while handling file created event." );
      logger.error( e );
    }
  }

  @Override
  public EventSubQueue getEventSubQueue()
  {
    return null;
  }
   

  public static String getLogFolder()
  {
    return logfolder;
  }
  
  
  public static String getLog()
  {
    return bootstraplog.toString();
  }


  
  public static void logToBuffer( String s )
  {
    if ( bootstraplog == null )
      return;
    
    synchronized ( bootstraplog )
    {
      bootstraplog.append( staticid );
      bootstraplog.append( " " );
      bootstraplog.append( s );
      bootstraplog.append( "\n" );
    }
  }

  public static void logToBuffer( Throwable th )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter( sw );
    th.printStackTrace( pw );
    ContextListener.logToBuffer( sw.toString() );
    ContextListener.logToBuffer( "\n" );
  }

  public static void setConfigFromHttpRequest( HttpServletRequest req )
  {
    for ( String name : allowedpropertynames )
    {
      String value = req.getParameter(name);
      if ( value != null && value.length() > 0 )
      {
        configproperties.setProperty(name, value);
        logger.info("Setting config key " + name + " to " + value );
      }
    }
    logger.info("Didn't actually save the configuration - just testing." );
  }
  
}

class BuildingBlockProperties extends Properties
{

}

