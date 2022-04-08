/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.data.content.Content;
import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author jon
 */
public class LinkInfo
{
  final CSResourceLinkWrapper link;
  final Date courselastaccessed;
  final String path;

  public LinkInfo(CSResourceLinkWrapper link, Date courselastaccessed)
  {
    this.link = link;
    this.courselastaccessed = courselastaccessed;
    this.path = link.getParentPath();
  }

  public CSResourceLinkWrapper getLink() {
    return link;
  }

  public Date getCourseLastAccessed() {
    return courselastaccessed;
  }

  public String getPath() {
    return path;
  }
}
