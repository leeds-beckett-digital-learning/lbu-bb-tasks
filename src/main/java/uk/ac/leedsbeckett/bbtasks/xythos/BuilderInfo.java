/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jon
 */
public class BuilderInfo
{
    final String id;
    final String familyname;
    final String name;
    final String email;
    final ArrayList<CourseInfo> courses = new ArrayList<>();

    public BuilderInfo( String id, String familyname, String name, String email )
    {
      this.id = id;
      this.familyname = familyname;
      this.name = name;
      this.email = email;
    }

    public String getId()
    {
      return id;
    }

    public String getFamilyname()
    {
      return familyname;
    }

    
    public String getName()
    {
      return name;
    }

    
    public String getEmail()
    {
      return email;
    }

    public List<CourseInfo> getCourses()
    {
      return courses;
    }

    
    
    public void addCourseInfo( CourseInfo ci )
    {
      courses.add( ci );
    }
  
}
