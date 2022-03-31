/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.persist.Id;
import java.util.Date;

/**
 *
 * @author jon
 */
public class CourseInfo
{
  final Id courseid;
  final Date lastaccessed;

  public CourseInfo(Id courseid, Date lastaccessed) {
    this.courseid = courseid;
    this.lastaccessed = lastaccessed;
  }

  public Id getCourseId() {
    return courseid;
  }

  public Date getLastAccessed() {
    return lastaccessed;
  }
  
  
}
