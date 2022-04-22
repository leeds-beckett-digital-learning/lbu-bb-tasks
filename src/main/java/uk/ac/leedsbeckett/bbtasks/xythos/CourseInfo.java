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
  final Id coursepkid;
  final String coursename;
  final Date lastaccessed;
  final String courseinstructorrole;
  
  String title;
  ArrayList<LinkInfo> links = new ArrayList<>();
  ArrayList<FileVersionInfo> files = new ArrayList<>();

  public static String getCourseInstructorRole( String coursename )
  {
    return "G:CR:" + coursename + ":INSTRUCTOR";
  }
  
  public CourseInfo(Id coursepkid, String coursename, Date lastaccessed) {
    this.coursepkid     = coursepkid;
    this.coursename   = coursename;
    this.lastaccessed = lastaccessed;
    this.courseinstructorrole = getCourseInstructorRole( coursename );
  }

  public Id getCoursePkId() {
    return coursepkid;
  }

  public String getCourseName() {
    return coursename;
  }

  public String getCourseInstructorRole()
  {
    return courseinstructorrole;
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

  public void addFile( FileVersionInfo file )
  {
    files.add( file );
  }

  public List<FileVersionInfo> getFiles()
  {
    return files;
  }
}
