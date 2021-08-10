/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

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
  public String getUsername()
  {
    return getProperty( "username" );
  }
  public void setUsername( String username )
  {
    setProperty( "username", username );
  }  
}
