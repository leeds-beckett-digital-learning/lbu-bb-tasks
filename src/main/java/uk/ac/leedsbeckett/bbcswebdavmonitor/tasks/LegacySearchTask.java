/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jon
 */
public class LegacySearchTask extends BaseTask
{

  String base = "/usr/local";
  String search;

  public LegacySearchTask(String search)
  {
    this.search = search;
  }

  private void listDirectoryMatch(File dir) throws InterruptedException
  {
    bbmonitor.logger.info("Searching in " + new File(base).getAbsolutePath());
    for (File f : dir.listFiles())
    {
      if ( Thread.interrupted() ) throw new InterruptedException();
      if (f.getName().equals(search))
      {
        bbmonitor.logger.info(f.getAbsolutePath());
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
    bbmonitor.logger.info("End of legacy file system search.");
  }
}
