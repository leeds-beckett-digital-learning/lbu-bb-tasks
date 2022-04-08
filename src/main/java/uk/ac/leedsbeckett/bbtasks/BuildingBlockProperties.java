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
  public String getEmailAddress()
  {
    return getProperty( "emailaddress" );
  }
  public void setEmailAddress( String emailaddress )
  {
    setProperty( "emailaddress", emailaddress );
  }  
  public String getEmailName()
  {
    return getProperty( "emailname" );
  }
  public void setEmailName( String emailname )
  {
    setProperty( "emailname", emailname );
  }  
}
