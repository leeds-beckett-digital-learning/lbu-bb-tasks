/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import blackboard.data.course.Course;
import blackboard.data.course.CourseManagerEx;
import blackboard.data.course.CourseManagerExFactory;
import blackboard.data.registry.CourseRegistryUtil;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

/**
 *
 * @author jon
 */
public class TriggerAutoarchiveTask extends BaseTask
{

  public Collection<String> courseids;

  public TriggerAutoarchiveTask(
          @JsonProperty("courseids") Collection<String> courseids
  )
  {
    this.courseids = courseids;
  }
  
  
  
  @Override
  public void doTask() throws InterruptedException
  {
    try
    {
      CourseManagerEx coursemanager = CourseManagerExFactory.getInstance();
      
      // convert courseid strings into Blackboard Id objects
      ArrayList<Id> ids = new ArrayList<>();
      for ( String str : courseids )
      {
        if ( !coursemanager.doesCourseIdExist( str ) )
        {
          webappcore.logger.error( "Specified course ID " + str + " does not exist." );
          return;
        }
        Course course = coursemanager.loadByCourseId( str );
        webappcore.logger.debug( "Converting course ID " + str + " (pkid = " + course.getId().toString() + ")" );
        ids.add( course.getId() );
      }
      
      // prepare a 'now' object
      Calendar now = Calendar.getInstance();
      now.setTimeInMillis( System.currentTimeMillis() );
      
      webappcore.logger.debug( "Setting date to " + now.toString() + " for " + ids.size() + " courses." );
      CourseRegistryUtil.addLastNonEnrolledAccessBatch( ids, now );
    }
    catch (PersistenceException ex)
    {
      webappcore.logger.error( "Unable to update the last non enrolled access timestamp.", ex );
    }

    webappcore.logger.info( "Task is complete." );
  }
  
}
