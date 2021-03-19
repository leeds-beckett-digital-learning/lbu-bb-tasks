/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import java.util.Properties;
import org.apache.log4j.Level;

/**
 *
 * @author jon
 */
public class BuildingBlockProperties extends Properties
{
  public BuildingBlockProperties(Properties defaults)
  {
    super(defaults);
  }
  public Level getLogLevel()
  {
    return Level.toLevel( getProperty("loglevel") );
  }
  public void setLogLevel( Level level )
  {
    setProperty( "loglevel", level.toString() );
  }
  public int getFileSize()
  {
    return Integer.parseInt( getProperty("filesize") );
  } 
  public void setFileSize( int fs )
  {
    setProperty( "filesize", Integer.toString( fs ) );
  }
  public String getAction()
  {
    return getProperty( "action" );
  }
  public void setAction( String action )
  {
    setProperty( "action", action );
  }
  public String getEMailSubject()
  {
    return getProperty( "emailsubject" );
  }
  public void setEMailSubject( String emailsubject )
  {
    setProperty( "emailsubject", emailsubject );
  }
  public String getEMailBody()
  {
    return getProperty( "emailbody" );
  }
  public void setEMailBody( String emailbody )
  {
    setProperty( "emailbody", emailbody );
  }
  public String getEMailFrom()
  {
    return getProperty( "emailfrom" );
  }
  public void setEMailFrom( String emailfrom )
  {
    setProperty( "emailfrom", emailfrom );
  }
  public String getEMailFromName()
  {
    return getProperty( "emailfromname" );
  }
  public void setEMailFromName( String emailfromname )
  {
    setProperty( "emailfromname", emailfromname );
  }
}
