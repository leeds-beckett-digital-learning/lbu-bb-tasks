/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.persist.Id;
import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import blackboard.platform.contentsystem.manager.ResourceLinkManager;
import blackboard.platform.contentsystem.service.ContentSystemServiceExFactory;
import blackboard.platform.course.CourseEnrollmentManager;
import blackboard.platform.course.CourseEnrollmentManagerFactory;
import blackboard.platform.gradebook2.CourseUserInformation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author jon
 */
public class BlobInfoMap
{
  public HashMap<Long,BlobInfo> blobmap                      = new HashMap<>();
  public ArrayList<BlobInfo> blobs                           = new ArrayList<>();
  public ArrayList<FileVersionInfo> allversions              = new ArrayList<>();
  public HashMap<String,FileVersionInfo> mapbypath           = new HashMap<>();
  public HashMap<String,FileVersionInfo> mapbyfileid         = new HashMap<>();
  
  public HashMap<String,ArrayList<LinkInfo>> linklistmap = new HashMap<>();

  public HashMap<Id,CourseInfo> coursemap = new HashMap<>();
  
  public void addBlobInfo( FileVersionInfo vi )
  {
    allversions.add( vi );
    mapbypath.put( vi.getPath(), vi ); 
    mapbyfileid.put( vi.getStringFileId(), vi ); 
    BlobInfo bi = blobmap.get( vi.getBlobId() );
    if ( bi == null )
    {
      bi = new BlobInfo( vi.getBlobId(), vi.getSize() );
      blobs.add( bi );
      blobmap.put( bi.getBlobId(), bi );
    }
    bi.getFileVersions().add( vi );
    linklistmap.put( vi.getStringFileId(), new ArrayList<>() );
  }
  
  public void addLinks( String strfileid, List<LinkInfo> links )
  {
    FileVersionInfo vi = mapbyfileid.get( strfileid );
    if ( vi != null )
    {
      BlobInfo bi = blobmap.get( vi.getBlobId() );
      if ( bi != null )
        bi.setLinkCount( bi.getLinkCount() + links.size() );
    }
    ArrayList<LinkInfo> copylist = new ArrayList<>();
    copylist.addAll( links );
    linklistmap.put(strfileid, copylist );
  }
  
  public CourseInfo getCourseInfo( Id cid )
  {
    return coursemap.get( cid );
  }
  
  public void addCourseInfo( CourseInfo courseinfo )
  {
    coursemap.put( courseinfo.courseid, courseinfo );
  }
  
  public void sortBlobs()
  {
    blobs.sort(
            (BlobInfo bia, BlobInfo bib) -> 
                {
                  if ( bia == null && bib == null ) return 0;
                  if ( bia == null ) return -1;
                  if ( bib == null ) return 1;
                  if ( bia.lastaccessed == null && bib.lastaccessed == null ) return Integer.compare( bia.linkcount, bib.linkcount );
                  if ( bia.lastaccessed == null ) return -1;
                  if ( bib.lastaccessed == null ) return 1;
                  return bia.lastaccessed.compareTo( bib.lastaccessed );
                }
    );
  }
  
  
  public void addBlackboardInfo()
  {
    ResourceLinkManager rlm = ContentSystemServiceExFactory.getInstance().getResourceLinkManager();
    CourseEnrollmentManager cemanager = CourseEnrollmentManagerFactory.getInstance();

    List<CSResourceLinkWrapper> rawlinks = null;
    List<LinkInfo> links = null;
    for ( BlobInfo bi : blobs )
    {
      for ( FileVersionInfo fvi : bi.getFileVersions() )
      {
        rawlinks = rlm.getResourceLinks( Long.toString( fvi.getFileId() ) + "_1" );
        links = new ArrayList<>();
        for ( CSResourceLinkWrapper rawlink : rawlinks )
        {
          Date latestdate=null;
          CourseInfo ci = getCourseInfo( rawlink.getCourseId() );
          if ( ci == null )
          {
            List<CourseUserInformation> studentlist = cemanager.getStudentByCourseAndGrader( rawlink.getCourseId(), null );
            if ( studentlist != null )
            {
              for ( CourseUserInformation student : studentlist )
              {
                Date d = student.getLastAccessDate();
                if ( d != null && (latestdate == null || d.after( latestdate )) )
                  latestdate = d;
              }
            }
            ci = new CourseInfo( rawlink.getCourseId(), latestdate );
            addCourseInfo( ci );
          }
          links.add( new LinkInfo( rawlink, ci.getLastAccessed() ) );
          bi.addLastAccessed( ci.getLastAccessed() );
        }
        addLinks( fvi.getStringFileId(), links );
      }      
    }
    sortBlobs();
    
  }
}
