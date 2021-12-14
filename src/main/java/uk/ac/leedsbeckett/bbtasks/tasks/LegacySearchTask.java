/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jon
 */
public class LegacySearchTask extends BaseTask
{

  transient String base = "/usr/local";
  public String search;

  @JsonCreator
  public LegacySearchTask( @JsonProperty("search") String search )
  {
    this.search = search;
  }

  private void listDirectoryMatch(File dir) throws InterruptedException
  {
    webappcore.logger.info("Searching in " + new File(base).getAbsolutePath());
    for (File f : dir.listFiles())
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      if (f.getName().equals(search))
      {
        webappcore.logger.info(f.getAbsolutePath());
      }
      if (f.isDirectory())
      {
        listDirectoryMatch(f);
      }
    }
  }

  @Override
  public void doTask() throws InterruptedException
  {
    listDirectoryMatch( new File(base) );
    webappcore.logger.info("End of legacy file system search.");
  }
}
