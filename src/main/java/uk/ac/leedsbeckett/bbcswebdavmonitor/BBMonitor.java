/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.data.user.User;
import blackboard.db.file.FileEntry;
import blackboard.persist.Id;
import blackboard.persist.user.UserDbLoader;
import blackboard.platform.intl.BbLocale;
import blackboard.platform.plugin.PlugInUtil;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.events.EventSubQueue;
import com.xythos.fileSystem.events.FileSystemEntryCreatedEventImpl;
import com.xythos.fileSystem.events.StorageServerEventBrokerImpl;
import com.xythos.fileSystem.events.StorageServerEventListener;
import com.xythos.security.api.Context;
import com.xythos.security.api.ContextFactory;
import com.xythos.security.api.PrincipalManager;
import com.xythos.security.api.UserBase;
import com.xythos.security.api.XythosCorePrincipalManager;
import com.xythos.storageServer.api.CreateDirectoryData;
import com.xythos.storageServer.api.CreateFileData;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemEntryCreatedEvent;
import com.xythos.storageServer.api.FileSystemEvent;
import com.xythos.storageServer.api.LockEntry;
import com.xythos.storageServer.api.VetoEventException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import javax.servlet.annotation.WebListener;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;


/**
 * This is the central object in this web application. It is instantiated once 
 * when the application starts because it is annotated as a WebListener. After
 * the servlet context is created the contextInitialized method of this class
 * is called. This object puts a reference to itself in an attribute of the
 * servlet context so that servlets can find it and interact with it.
 * 
 * @author jon
 */
@WebListener
public class BBMonitor implements ServletContextListener, StorageServerEventListener
{
  public final static String ATTRIBUTE_CONTEXTBBMONITOR = BBMonitor.class.getCanonicalName();
  private final static AtomicInteger instancecount = new AtomicInteger(0);
  private static StringBuilder bootstraplog = new StringBuilder();

  /**
   * servercoordinator communicates with BBMonitor objects on other servers
   * in the cluster. It works out which server is currently monitoring the 
   * creation of big files and it tells all servers if the configuration needs
   * to be reloaded.
   */
  ServerCoordinator servercoordinator;
  
  
  /**
   * logger is for technical/diagnostic information.
   */
  public Logger logger = null;
  
  /**
   * datalogger is where the creation of big files by users is logged.
   */
  public Logger datalogger = null;
  
  RollingFileAppender datarfapp = null;
  private final Properties defaultproperties = new Properties();
  private final BuildingBlockProperties configproperties = new BuildingBlockProperties(defaultproperties);
  private String logfolder;
  private BbLocale locale = new BbLocale();
  String instanceid;
  String buildingblockhandle;
  String buildingblockvid;
  String pluginid;
  boolean monitoringxythos=false;
  String serverid;  
  int filesize=100;  // in mega bytes
  File propsfile;

  private Class[] listensfor = {FileSystemEntryCreatedEventImpl.class};
  
  VirtualServer xythosvserver;
  String lockfilepath;
  String xythosprincipalid;
  UserBase xythosadminuser;  
  
  /**
   * The constructor just checks to see how many times it has been called.
   * This constructor is called by the servlet container.
   */
  public BBMonitor()
  {
    int count =  instancecount.incrementAndGet();
    instanceid = "BBMonitor_" + count;
    BBMonitor.logToBuffer("ContextListener constructor." );    
    if ( count > 1 )
      BBMonitor.logToBuffer("WHOOOOOAAAAAH. This constructor has been called more than once in this class loader!" );
  }
  
  
  /**
   * This method gets called by the servlet container after the servlet 
   * context has been set up. So, this is where BBMonitor initialises itself.
   * 
   * @param sce This servlet context event includes a reference to the servlet context.
   */
  @Override
  public void contextInitialized(ServletContextEvent sce)
  {
    BBMonitor.logToBuffer("BB plugin init");
    sce.getServletContext().setAttribute( ATTRIBUTE_CONTEXTBBMONITOR, this );
    try
    {
      serverid = InetAddress.getLocalHost().getHostName();
      BBMonitor.logToBuffer( serverid );
    }
    catch (UnknownHostException ex)
    {
      BBMonitor.logToBuffer( "Unable to find local IP address." );
      BBMonitor.logToBuffer( ex );
    }
    
    if ( !initDefaultSettings( sce ) )
      return;
    
    if ( !loadSettings() )
      return;

    if ( !initXythos() )
      return;
    
    servercoordinator = new ServerCoordinator( this, pluginid );
    if ( !servercoordinator.startInterserverMessaging() )
      return;
  }


  /**
   * Default settings are built into the web application. Here they are loaded
   * and also two key entries which are used to locate folders in the BB system.
   * @param sce
   * @return 
   */
  public boolean initDefaultSettings(ServletContextEvent sce)
  {
    String strfile = sce.getServletContext().getRealPath("WEB-INF/defaultsettings.properties" );
    BBMonitor.logToBuffer("Expecting to find default properties here: " + strfile );
    File file = new File( strfile );
    if ( !file.exists() )
    {
      BBMonitor.logToBuffer("It doesn't exist - cannot start." );
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
      BBMonitor.logToBuffer( "Cannot work out bb handle or vendor id so can't load configuration." );
      return false;      
    }
    pluginid = buildingblockvid + "_" + buildingblockhandle;
    
    return true;
  }
  
  /**
   * Load the variable properties file and start logging.
   * @return Success
   */
  public boolean loadSettings()
  {
    try
    {
      File strdir = PlugInUtil.getConfigDirectory(buildingblockvid, buildingblockhandle);
      BBMonitor.logToBuffer( "Building block config directory is here: " + strdir );
      logfolder = strdir.getPath() + "/log/";
      BBMonitor.logToBuffer( "Log folder here: " + logfolder );
      
      initLogging();
      
      propsfile = new File( strdir, buildingblockhandle + ".properties" );
      logger.info("Config properties file is here: " + propsfile );
      if ( !propsfile.exists() )
      {
        logger.info( "Doesn't exist so creating it now." );
        propsfile.createNewFile();
      }
      try ( FileReader reader = new FileReader( propsfile ) )
      {
        configproperties.load(reader);
      }
      catch (Exception ex)
      {
        logger.error( ex );
        return false;
      }
      logger.info( "Loaded configuration." );      
    }
    catch ( Throwable th )
    {
      BBMonitor.logToBuffer( "Failed to load configuration." );
      logToBuffer( th );
      return false;            
    }

    filesize = configproperties.getFileSize();
    logger.setLevel( configproperties.getLogLevel() );
    
    return true;
  }
  
  /**
   * Manually configure logging so that the log files for this application
   * go where we want them and not into general log files for BB.
   * @param logfilefolder 
   */
  public void initLogging(  ) throws IOException
  {
    File logdir = new File( logfolder );
    if ( !logdir.exists() )
      logdir.mkdir();

    
    Logger rootlog = LogManager.getLoggerRepository().getRootLogger();
    if ( rootlog == null )
      BBMonitor.logToBuffer( "No root log found." );
    else
      BBMonitor.logToBuffer( "Root log: " + rootlog.getName() );
    
    logger = LogManager.getLoggerRepository().getLogger(BBMonitor.class.getName() );
    logger.setLevel( Level.INFO );
    String logfilename =  logfolder + serverid + ".log";
    BBMonitor.logToBuffer( logfilename );
    RollingFileAppender rfapp = 
        new RollingFileAppender( 
            new PatternLayout( "%d{ISO8601} %-5p: %m%n" ), 
            logfilename, 
            true );
    rfapp.setMaxBackupIndex( 20 );
    rfapp.setMaxFileSize( "100MB" );
    logger.removeAllAppenders();
    logger.addAppender( rfapp );
    
    datalogger = LogManager.getLoggerRepository().getLogger(BBMonitor.class.getName() + "/datalogger" );
    datalogger.setLevel( Level.INFO );
    datalogger.removeAllAppenders();
  }
  
  
  
  public boolean initXythos()
  {
    Context context = null;
    xythosvserver = VirtualServer.getDefaultVirtualServer();
    logger.info( "Default xythos virtual server " + xythosvserver.getName() );
    try
    {
      for ( String location : PrincipalManager.getUserLocations() )
      {
        logger.info( "User Location: " + location );
        xythosadminuser = PrincipalManager.findUser( configproperties.getProperty("username"), location );
        if ( xythosadminuser == null )
          logger.info( "Did not find user here." );
        else
        {
          logger.info( "User: " + xythosadminuser.getID() + " " + xythosadminuser.getPrincipalID() + " " + xythosadminuser.getDisplayName() + " " + xythosadminuser.getLocation() );
          break;
        }
      }

      if ( xythosadminuser == null )
      {
        logger.error( "Unable to find user " + configproperties.getProperty("username") );
        return false;
      }
        
      FileSystemEntry pluginsdir, plugindir, lockfile;
      context = ContextFactory.create( xythosadminuser, new Properties() );
      pluginsdir = FileSystem.findEntry( xythosvserver, "/internal/plugins", false, context );
      if ( pluginsdir == null )
      {
        logger.error( "Can't find plugins directory in xythos." );
        return false;
      }
      
      xythosprincipalid = xythosadminuser.getPrincipalID();      
      plugindir = FileSystem.findEntry( xythosvserver, "/internal/plugins/" + pluginid, false, context );
      if ( plugindir == null )
      {
        logger.info( "Creating subfolder " + pluginid );
        CreateDirectoryData cdd = new CreateDirectoryData( xythosvserver, "/internal/plugins/", pluginid, xythosprincipalid );
        plugindir = FileSystem.createDirectory( cdd, context );
      }
      if ( !(plugindir instanceof FileSystemDirectory ) )
      {
        logger.error( "Expecting directory named /internal/plugins/" +  pluginid + ". But it is not a directory." );
        return false;
      }
      
      lockfilepath = "/internal/plugins/" + pluginid + "/lockfile";
      lockfile = FileSystem.findEntry( xythosvserver, lockfilepath, false, context );
      if ( lockfile == null )
      {
        logger.info( "Creating lock file." );
        byte[] buffer = "Lock file.".getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream( buffer );
        CreateFileData cfd = new CreateFileData( xythosvserver, "/internal/plugins/" + pluginid, "lockfile", "text/plain", xythosprincipalid, bais );
        lockfile = FileSystem.createFile( cfd, context );
      }

      // commit regardless of whether it was necessary to create the file.
      // this is to ensure resources are released.
      context.commitContext();
    }
    catch (Exception ex)
    {
      logger.error( "Exception trying to initialise Xythos content collection files." );
      logger.error( ex );
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch (Exception ex1) { logger.error( "Unable to roll back Xythos context after exception" ); logger.error( ex ); }
      }
      return false;
    }

    return true;
  }
  
  
  /**
   * This is called when the servlet context is being shut down.
   * @param sce 
   */
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

  /**
   * This is called by the server coordinator if one of the servers
   * indicates that settings have changed. So, this server must load the
   * settings and reconfigure.
   */
  public void reloadSettings()
  {
    logger.info( "reloadSettings()" );
    try ( FileReader reader = new FileReader( propsfile ) )
    {
      configproperties.load(reader);
      filesize = configproperties.getFileSize();
      logger.setLevel( configproperties.getLogLevel() );
    }
    catch (Exception ex)
    {
      logger.error( "Unable to load properties from file." );
      logger.error( ex );
    }    
  }

  public BuildingBlockProperties getProperties()
  {
    return configproperties;
  }


  public void saveProperties()
  {
    try ( FileWriter writer = new FileWriter( propsfile ) )
    {
      configproperties.store(writer, serverid);
    } catch (IOException ex)
    {
      logger.error( "Unable to save properties to file." );
      logger.error( ex );
    }
    servercoordinator.broadcastConfigChange();
  }

  
  
  /**  
   * This is called when this server becomes responsible for monitoring the
   * Xythos content collection. It must connect to the data log file and
   * register with Xythos.
   */
  protected void startMonitoringXythos()
  {
    if ( monitoringxythos )
      return;
    try
    {
      logger.info("Starting listening to Xythos." );
    
      String logfilename =  logfolder + "bigfiles.log";
      logger.info(logfilename );
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

  /**
   * Closes the data log file so another server can open it. This de-registers
   * with Xythos.
   */
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
  
  
  /**
   * Part of the implementation of StorageServerEventListener interface.
   * @return List of event classes we are interested in.
   */
  @Override
  public Class[] listensFor()
  {
    return listensfor;
  }

  /**
   * Part of the implementation of StorageServerEventListener interface.
   * Receives notification of events.  Some events are selected for logging.
   * Before logging the user ID string from Xythos is converted into a BB
   * User object so information about the user who created the file can be
   * logged.
   */
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
      if ( fsece.getSize() < (1024*1024*filesize) )
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

  /**
   * Part of the implementation of StorageServerEventListener interface.
   * @return In our case there is no sub queue - returns null.
   */
  @Override
  public EventSubQueue getEventSubQueue()
  {
    return null;
  }
   

  /**
   * For servlet to find out where logs are located.
   * @return Full path of this app's log folder.
   */
  public String getLogFolder()
  {
    return logfolder;
  }
  
  
  /**
   * For servlet - returns the logging text that was recorded before the
   * proper logs on file were initialised.
   * @return 
   */
  public static String getBootstrapLog()
  {
    return bootstraplog.toString();
  }


  /**
   * Logs to a string buffer while this object is initializing
   * @param s 
   */
  private static void logToBuffer( String s )
  {
    if ( bootstraplog == null )
      return;
    
    synchronized ( bootstraplog )
    {
      bootstraplog.append( s );
      bootstraplog.append( "\n" );
    }
  }

  /**
   * Logs a Throwable to the bootstrap log.
   * @param th 
   */
  private static void logToBuffer( Throwable th )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter( sw );
    th.printStackTrace( pw );
    BBMonitor.logToBuffer( sw.toString() );
    BBMonitor.logToBuffer( "\n" );
  }
  
}


