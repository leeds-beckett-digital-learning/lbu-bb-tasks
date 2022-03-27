/*
 * Copyright 2022 maber01.
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
package uk.ac.leedsbeckett.bbtasks.tasks.data;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author maber01
 */
public class XythosDirectoryInfo
{

  final String name;
  final String principal;
  final String courseid;
  final String courseinstructorrole;
  
  public XythosDirectoryInfo( String name, String principal )
  {
    this.name = name;
    this.principal = principal;
    String[] list = name.split( "/" );
    courseid = list[ list.length - 1 ];
    courseinstructorrole = "G:CR:" + courseid + ":INSTRUCTOR";
  }

  public String getName()
  {
    return name;
  }

  public String getPrincipal()
  {
    return principal;
  }

  public String getCourseId()
  {
    return courseid;
  }

  public String getCourseInstructorRole()
  {
    return courseinstructorrole;
  }
}
