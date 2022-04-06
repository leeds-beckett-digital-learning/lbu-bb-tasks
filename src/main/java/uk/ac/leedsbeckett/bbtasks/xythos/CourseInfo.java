/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.persist.Id;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author jon
 */
public class CourseInfo
{
  final Id courseid;
  final Date lastaccessed;
  String title;
  ArrayList<LinkInfo> links = null;

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
  
  public void addLink( LinkInfo link )
  {
    links.add( link );
  }

  public List<LinkInfo> getLinks()
  {
    return links;
  }
}
