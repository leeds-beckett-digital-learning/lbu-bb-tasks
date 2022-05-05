/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.data.content.Content;
import blackboard.data.content.ContentFile;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.content.ContentFileDbLoader;
import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import blackboard.platform.coursecontent.CourseContentManager;
import blackboard.platform.coursecontent.CourseContentManagerFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List; 
import org.apache.log4j.Logger;

/**
 * Notes about how the CSResourceLinkWrapper 'link' relates to database records.
 * 
 * When link type is 'attachment' links.getParentPath does not return the path,
 * just the name of the attachment link. How to determine the path?
 * 
 * link.getLink().getId()         == cms_resource_link.pk1
 * link.getLink().getParentId()   == cms_resource_link.parent_pk1
 * 
 * when the type of link is 'contentfile'
 * 
 * link.getLink().getParentId()   == files.pk1  == course_contents_files.files_pk1
 * 
 * The course_contents_files table also contains field course_contents_files.course_contents_pk1
 * which corresponds to course_contents.pk1
 * 
 * The course contents table can be used to recreate the path to the attachment.
 * 
 * 
 * @author jon
 */
public class LinkInfo
{
  final CSResourceLinkWrapper link;
  final Date courselastaccessed;
  final String path;
  final Logger debuglogger;
  
  Id contentfileid = null;
  Id contentid = null;
  
  public LinkInfo( CSResourceLinkWrapper link, Date courselastaccessed, Logger debuglogger )
  {
    this.link = link;
    this.courselastaccessed = courselastaccessed;
    this.path = findPath();
    this.debuglogger = debuglogger;
  }

  private String findPath()
  {
    if ( !(link.getParentEntity() instanceof ContentFile) )
      return link.getParentPath();
    
    ContentFile cf = (ContentFile)link.getParentEntity();
    // This object was not loaded from database and the content id is not
    // filled in. But the pk1 value is.
    contentfileid = cf.getId();
      
    try
    {
      contentid = ContentIdLoader.loadContentIdByFileId( contentfileid );
    }
    catch (PersistenceException | SQLException | ConnectionNotAvailableException ex)
    {
      debuglogger.error( "Unable to find content ID from file ID.", ex );
      return link.getParentDisplayName() + " (1)";
    }
    
    if ( contentid == null )
      return link.getParentDisplayName() + " (2a)";
    if ( !contentid.isSet() )
      return link.getParentDisplayName() + " (2b)";
          
    CourseContentManager ccm = CourseContentManagerFactory.getInstance();
    Content grandparent;
    try
    {
      grandparent = ccm.getContent( contentid );
      grandparent.getId();
    }
    catch ( PersistenceException | NullPointerException ex )
    {
      debuglogger.error( "Unable to load gradparent.", ex );
      return link.getParentDisplayName() + " (3)";
    }

    // create our own link wrapper and use it to find path of grandparent
    CSResourceLinkWrapper parentlink = new CSResourceLinkWrapper( link.getLink(), link.getCourse(), grandparent );
    return parentlink.getParentPath() + " (4)";
  }
  
  public CSResourceLinkWrapper getLink() {
    return link;
  }

  public Date getCourseLastAccessed() {
    return courselastaccessed;
  }

  public Id getContentId()
  {
    return contentid;
  }
  
  public Id getContentFileId()
  {
    return contentfileid;
  }
  
  public String getPath() {
    return path;
  }
}
