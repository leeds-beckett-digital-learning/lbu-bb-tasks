/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

import blackboard.platform.plugin.PlugInUtil;
import com.xythos.common.api.VirtualServer;
import com.xythos.security.api.PrincipalManager;
import com.xythos.security.api.UserBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import javax.servlet.annotation.WebListener;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import uk.ac.leedsbeckett.bbb2utils.messaging.MessageHeader;
import uk.ac.leedsbeckett.bbtasks.messaging.ConfigMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.CoordinatorClientMessageListener;
import uk.ac.leedsbeckett.bbtasks.messaging.InterserverMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.RequestStartTimeMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.RequestTaskListMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.StartTimeMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.TaskListMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.TaskMessage;
import uk.ac.leedsbeckett.bbtasks.tasks.BaseTask;


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
public class WebAppCore implements ServletContextListener, CoordinatorClientMessageListener
{
  public  final static String ATTRIBUTE_CONTEXTBBMONITOR = WebAppCore.class.getCanonicalName();
  private final static StringBuilder bootstraplog = new StringBuilder();  
  public  final static SimpleDateFormat dateformatforfilenames = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");


  MyInterserverClient coordclient;
  TaskManager taskmanager;
  long starttime;
  
  /**
   * logger is for technical/diagnostic information.
   */
  public Logger logger = null;
  
  
  RollingFileAppender datarfapp                          = null;
  private final Properties defaultproperties             = new Properties();
  private final BuildingBlockProperties configproperties = new BuildingBlockProperties(defaultproperties);
  String contextpath;
  String buildingblockhandle;
  String buildingblockvid;
  String pluginid;
  String serverid;  
  File propsfile;

  VirtualServer xythosvserver;
  String xythosprincipalid;
  UserBase xythosadminuser;  
  
  
  public Path virtualserverbase=null;
  public Path pluginbase=null;
  public Path logbase=null;
  public Path configbase=null;
  

  PendingStartTimeRequest pendingstreq = null;
        
  /**
   * The constructor just checks to see how many times it has been called.
   * This constructor is called by the servlet container.
   */
  public WebAppCore()
  {
    WebAppCore.logToBuffer("ContextListener constructor." );    
  }

  public Path getVirtualserverbase()
  {
    return virtualserverbase;
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
    WebAppCore.logToBuffer("BB plugin init");
    starttime = System.currentTimeMillis();
    sce.getServletContext().setAttribute( ATTRIBUTE_CONTEXTBBMONITOR, this );
    try
    {
      serverid = InetAddress.getLocalHost().getHostName();
      WebAppCore.logToBuffer( serverid );
    }
    catch (UnknownHostException ex)
    {
      WebAppCore.logToBuffer( "Unable to find local IP address." );
      WebAppCore.logToBuffer( ex );
    }
    
    if ( !initDefaultSettings( sce ) )
      return;
    
    if ( !loadSettings() )
      return;

    installResources();
    
    if ( !initXythos() )
      return;
    
    contextpath = sce.getServletContext().getContextPath();
    
    coordclient = new MyInterserverClient( logger );
    coordclient.addListener( this );
    coordclient.initialize( configproperties.getUsername(), pluginid, serverid );

    if ( coordclient.isFailed() )
    {
      logger.error( "Unable to initialise interaction with Xythos subsystem." );
      return;
    }
    coordclient.startProcessing();    
    
    taskmanager = new TaskManager( this );
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
    WebAppCore.logToBuffer("Expecting to find default properties here: " + strfile );
    File file = new File( strfile );
    if ( !file.exists() )
    {
      WebAppCore.logToBuffer("It doesn't exist - cannot start." );
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
      WebAppCore.logToBuffer( "Cannot work out bb handle or vendor id so can't load configuration." );
      return false;      
    }
    pluginid = buildingblockvid + "_" + buildingblockhandle;
    
    try
    {
      configbase = Paths.get( PlugInUtil.getConfigDirectory( buildingblockvid, buildingblockhandle ).getPath() );
      logbase    = configbase.resolve( "log" );
      pluginbase = configbase.getParent();      
      Path p = pluginbase; 
      while ( p.getNameCount() > 2 )
      {
        if ( "vi".equals( p.getParent().getFileName().toString() ) )
          break;
        p = p.getParent();
      }
      virtualserverbase = p;

      WebAppCore.logToBuffer( "virtualserverbase = " + virtualserverbase.toString() );
      WebAppCore.logToBuffer( "pluginbase        = " + pluginbase.toString() );
      WebAppCore.logToBuffer( "configbase        = " + configbase.toString() );
      WebAppCore.logToBuffer( "logbase           = " + logbase.toString()    );
    }
    catch ( Exception e )
    {
      WebAppCore.logToBuffer( e );      
    }
    
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
      initLogging();
      
      propsfile = configbase.resolve( buildingblockhandle + ".properties" ).toFile();
      logger.info("Config properties file is here: " + propsfile );
      if ( !propsfile.exists() )
      {
        logger.info( "Doesn't exist so creating it now." );
        propsfile.createNewFile();
      }
      
      return reloadSettings();
    }
    catch ( Throwable th )
    {
      WebAppCore.logToBuffer( "Failed to load configuration." );
      logToBuffer( th );
      return false;            
    }
  }
  
  /**
   * Manually configure logging so that the log files for this application
   * go where we want them and not into general log files for BB.
   * @param logfilefolder 
   */
  public void initLogging(  ) throws IOException
  {
    if ( !Files.exists( logbase ) )
      Files.createDirectory( logbase );
    
    Logger rootlog = LogManager.getLoggerRepository().getRootLogger();
    if ( rootlog == null )
      WebAppCore.logToBuffer( "No root log found." );
    else
      WebAppCore.logToBuffer( "Root log: " + rootlog.getName() );
    
    logger = LogManager.getLoggerRepository().getLogger(WebAppCore.class.getName() );
    logger.setLevel( Level.INFO );
    String logfilename = logbase.resolve( serverid + ".log" ).toString();
    WebAppCore.logToBuffer( logfilename );
    RollingFileAppender rfapp = 
        new RollingFileAppender( 
            new PatternLayout( "%d{ISO8601} %-5p: %m%n" ), 
            logfilename, 
            true );
    rfapp.setMaxBackupIndex( 100 );
    rfapp.setMaxFileSize( "2MB" );
    logger.removeAllAppenders();
    logger.addAppender( rfapp );
    logger.info( "==========================================================" );
    logger.info( "Log file has been opened." );
    logger.info( "==========================================================" );    
  }
  
  public void installResources()
  {
    File base = new File( pluginbase.toFile(), "ffmpegbin" );
    if ( !base.isDirectory() )
      base.mkdir();
    
    try ( BufferedReader reader = new BufferedReader( new InputStreamReader( getClass().getClassLoader().getResourceAsStream("/uk/ac/leedsbeckett/bbcswebdavmonitor/resources/ffmpegbin/filelist.txt") ) ); )
    {
      Stream<String> slines = reader.lines();
      Object[] olines = slines.toArray();
      for ( Object oline : olines )
      {
        String filename = oline.toString().trim();
        if ( filename.length() == 0 || filename.endsWith( "/filelist.txt" ) )
          continue;
        String resourcename = "/uk/ac/leedsbeckett/bbcswebdavmonitor/resources/ffmpegbin/" + filename;
        File file = new File( pluginbase.toFile(), "ffmpegbin/" + filename );
        logger.info( "Checking " + file );
        if ( !file.isFile() )
        {
          try ( InputStream   in = getClass().getClassLoader().getResourceAsStream( resourcename );
                OutputStream out = new FileOutputStream( file ) )
          {
            IOUtils.copy(in, out);
          }
        }        
      }

      File ffmpegfile = new File( pluginbase.toFile(), "ffmpegbin/ffmpeg" );
      if ( ffmpegfile.exists() && ffmpegfile.isFile() )
        ffmpegfile.setExecutable(true);
    }
    catch (IOException ex)
    {
      logger.error( "Unable to unpack resources.", ex );
    }
  }
  
  
  public boolean initXythos()
  {
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
    }
    catch (Exception ex)
    {
      logger.error( "Exception trying to initialise Xythos content collection files." );
      logger.error( ex );
      return false;
    }

    return true;
  }
  
  
  /**
   * This is called when the servlet context is being shut down.
   * @param sce 
   */
  @Override
  public void contextDestroyed( ServletContextEvent sce )
  {
    logger.info("BB plugin destroy");
    if ( taskmanager != null )
      taskmanager.shutdown();
    try { if ( coordclient != null) coordclient.stopProcessing(); }
    catch ( Throwable th ) { logger.error( "Exception trying to stop Xythos monitoring", th ); }
  }

  /**
   * This is called by the server coordinator if one of the servers
   * indicates that settings have changed. So, this server must load the
   * settings and reconfigure.
   */
  public boolean reloadSettings()
  {
    logger.info( "reloadSettings()" );
    try ( FileReader reader = new FileReader( propsfile ) )
    {
      configproperties.load(reader);
      logger.setLevel( configproperties.getLogLevel() );
      return true;
    }
    catch (Exception ex)
    {
      logger.error( "Unable to load properties from file.", ex );
      return false;
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
      coordclient.sendMessage( new ConfigMessage() );
    } catch (IOException ex)
    {
      logger.error( "Unable to save properties to file." );
      logger.error( ex );
    }
    try{Thread.sleep( 5000 );} catch (InterruptedException ex){}
  }

  
    
  /**
   * For servlet to find out where logs are located.
   * @return Full path of this app's log folder.
   */
  public String getLogFolder()
  {
    return this.logbase.toString();
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
    WebAppCore.logToBuffer( sw.toString() );
    WebAppCore.logToBuffer( "\n" );
  }

  
  public void requestTask( BaseTask task ) throws TaskException
  {
    try
    {
      // Which server should we request?
      RequestStartTimeMessage rqstartm = new RequestStartTimeMessage();
      rqstartm.id = System.currentTimeMillis();
      pendingstreq = new PendingStartTimeRequest( rqstartm );
      coordclient.sendMessage( rqstartm );

      try { Thread.sleep( 1000L * 5L ); }
      catch ( InterruptedException ex ) { throw new TaskException( "Task request was interrupted - perhaps due to pending server shut down." ); }

      String targetserverid = pendingstreq.getOldestServerId();
      if ( targetserverid == null )
        throw new TaskException( "Unable to find a server to run the task on." );
      
      logger.info( "Servers that replied:\n" + pendingstreq.toString() );
      logger.info( "Best choice of server for task is " + pendingstreq.getOldestServerId() );    
      
      TaskMessage taskmessage = new TaskMessage();
      taskmessage.setTask( task );
      coordclient.sendMessage( taskmessage, targetserverid );
    }
    finally
    {
      pendingstreq = null;
    }
  }

  @Override
  public void receiveMessage( MessageHeader header, InterserverMessage message)
  {
    if ( message instanceof ConfigMessage )
    {
      logger.info( "Reloading config... " + (reloadSettings()?"success":"failed") );
      return;
    }

    if ( message instanceof RequestStartTimeMessage )
    {
      logger.info( "Server " + header.fromServer + " requests our start time." );
      RequestStartTimeMessage rqstm = (RequestStartTimeMessage)message;
      StartTimeMessage stm = new StartTimeMessage();
      stm.id = rqstm.id;
      stm.starttime = starttime;
      stm.serverid = serverid;
      coordclient.sendMessage( stm, header.fromServer );
      return;
    }

    if ( message instanceof StartTimeMessage )
    {
      StartTimeMessage stm = (StartTimeMessage)message;
      logger.info( "Server " + header.fromServer + " says it started at " + stm.starttime );
      if ( pendingstreq != null )
      {
        if ( stm.id == pendingstreq.getId() )
          pendingstreq.addStartTimeMessage( stm );
      }
      return;
    }

    if ( message instanceof RequestTaskListMessage )
    {
      logger.info( "RequestTaskListMessage...  ...not implemented yet." );
      return;
    }
    
    if ( message instanceof TaskListMessage )
    {
      logger.info( "TaskListMessage...  ...not implemented yet." );
      return;
    }
    
    if ( message instanceof TaskMessage )
    {
      TaskMessage tm = (TaskMessage)message;
      BaseTask task = tm.getTask();
      if ( task != null )
      {
        logger.info( "    task class = " + task.getClass().getCanonicalName() );
        task.setWebAppCore( this );
        taskmanager.queueTask( task );
      }
      return;
    }
   
    logger.info( "Unknown class of message: " + message.getClass().getCanonicalName() );
  }
  
  
  public class PendingStartTimeRequest
  {
    RequestStartTimeMessage message;
    ArrayList<StartTimeMessage> responselist = new ArrayList<>();
    
    public PendingStartTimeRequest( RequestStartTimeMessage message )
    {
      this.message = message;
    }

    public long getId()
    {
      return message.id;
    }
    
    public synchronized void addStartTimeMessage( StartTimeMessage message )
    {
      responselist.add( message );
    }
    
    public synchronized String getOldestServerId()
    {
      if ( responselist.size() == 0 )
        return null;
      responselist.sort( new Comparator<StartTimeMessage>() {
        @Override
        public int compare(StartTimeMessage o1, StartTimeMessage o2)
        {
          if ( o1.starttime < o2.starttime )
            return 1;
          if ( o1.starttime > o2.starttime )
            return -1;
          return o1.serverid.compareTo( o2.serverid );
        }
      } );
      return responselist.get( 0 ).serverid;
    }
    
    public synchronized String toString()
    {
      StringBuilder sb = new StringBuilder();
      for ( StartTimeMessage stm : responselist )
      {
        sb.append( stm.serverid  );
        sb.append( " "           );
        sb.append( stm.starttime );
        sb.append( "\n"          );
      }
      return sb.toString();
    }
  }
}


