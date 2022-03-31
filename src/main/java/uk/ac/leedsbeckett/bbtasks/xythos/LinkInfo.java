/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import java.util.Date;

/**
 *
 * @author jon
 */
public class LinkInfo
{
  final CSResourceLinkWrapper link;
  final Date courselastaccessed;

  public LinkInfo(CSResourceLinkWrapper link, Date courselastaccessed)
  {
    this.link = link;
    this.courselastaccessed = courselastaccessed;
  }

  public CSResourceLinkWrapper getLink() {
    return link;
  }

  public Date getCourseLastAccessed() {
    return courselastaccessed;
  }  
}
