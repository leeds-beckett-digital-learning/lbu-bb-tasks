/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.tasks;

import java.util.Random;

/**
 *
 * @author jon
 */
public class DemoTask extends BaseTask
{
  @Override
  public void run()
  {
    bbmonitor.logger.info( "Demo task started." );
    long started = System.currentTimeMillis();
    Random r = new Random();
    double sum  = 0.0;
    long i, n=0;
    for ( i=1; i<=(1024L*1024L*1024L); i++ )
    {
      sum += r.nextGaussian();
      n++;
      if ( Thread.interrupted() )
      {
        bbmonitor.logger.info( "Demo task interrupted. Stopping early." );
        break;
      }
    }
    double mean = sum / (double)n;
    long now = System.currentTimeMillis();
    bbmonitor.logger.info( "Mean of " + i + " random numbers = " + mean );
    bbmonitor.logger.info( "Demo task completed in " + (now-started) + " milliseconds." );
  }
}
