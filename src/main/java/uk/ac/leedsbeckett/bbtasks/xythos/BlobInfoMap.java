/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.base.BbList;
import blackboard.data.course.Course;
import blackboard.data.course.CourseManagerEx;
import blackboard.data.course.CourseManagerExFactory;
import blackboard.data.course.CourseMembership;
import blackboard.data.user.User;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipSearch;
import blackboard.persist.user.UserDbLoader;
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
import java.util.logging.Level;
import org.apache.log4j.Logger;
import uk.ac.leedsbeckett.bbtasks.TaskException;
import uk.ac.leedsbeckett.bbtasks.tasks.XythosArchiveHugeCourseFilesStageTwoTask;

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

  public HashMap<Id,CourseInfo> coursebypkidmap = new HashMap<>();
  public HashMap<String,CourseInfo> coursebycourseidmap = new HashMap<>();
  public HashMap<String,BuilderInfo> buildermap = new HashMap<>();
  public ArrayList<BuilderInfo> builders = new ArrayList<>();
        
  boolean addbuilders = true;
  
  final Logger debuglogger;

  public BlobInfoMap( Logger debuglogger )
  {
    this.debuglogger = debuglogger;
  }
  
  
  
  public void addBlobInfo( FileVersionInfo vi )
  {
    allversions.add( vi );
    mapbypath.put( vi.getPath(), vi ); 
    mapbyfileid.put( vi.getStringFileId(), vi ); 
    BlobInfo bi = blobmap.get( vi.getBlobId() );
    if ( bi == null )
    {
      bi = new BlobInfo( vi.getBlobId(), vi.getSize(), vi.getDigest() );
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
    return coursebypkidmap.get( cid );
  }

  private CourseInfo getCourseInfo( CSResourceLinkWrapper rawlinkwrapper )
  {
    if ( coursebypkidmap.containsKey( rawlinkwrapper.getCourseId() ) )
      return coursebypkidmap.get( rawlinkwrapper.getCourseId() );
    
    return createCourseInfo( rawlinkwrapper.getCourse() );
  }
  
  private CourseInfo getCourseInfo( String course_id )
  {
    if ( coursebycourseidmap.containsKey( course_id ) )
      return coursebycourseidmap.get( course_id );
    
    Course course = null;
    CourseManagerEx coursemanager = CourseManagerExFactory.getInstance();
    try
    {
      course = coursemanager.loadByCourseId( course_id );
    }
    catch (PersistenceException ex)
    {
      debuglogger.info( "    Unable to find course [" + course_id + "]" );
      return null;
    }

    if ( course == null )
    {
      debuglogger.info( "    Didn't find course " + course_id );
      return null;
    }

    debuglogger.info( "    Found course " + course.getId().getExternalString() + " = " + course_id );

    return createCourseInfo( course );
  }
  
  private CourseInfo createCourseInfo( Course course )
  {
    Date latestdate = getLatestAccessedDate( course.getId() );
    CourseInfo courseinfo = new CourseInfo( course.getId(), course.getCourseId(), latestdate );
    if ( addbuilders )
      addBuilders( courseinfo );
    coursebycourseidmap.put( course.getCourseId(), courseinfo );
    coursebypkidmap.put( course.getId(), courseinfo );
    return courseinfo;
  }
  

  public Date getLatestAccessedDate( Id course_pkid )
  {
    CourseEnrollmentManager cemanager = CourseEnrollmentManagerFactory.getInstance();
    Date latestdate=null;
    
    List<CourseUserInformation> studentlist = cemanager.getStudentByCourseAndGrader( course_pkid, null );
    if ( studentlist != null )
    {
      for ( CourseUserInformation student : studentlist )
      {
        Date d = student.getLastAccessDate();
        if ( d != null && (latestdate == null || d.after( latestdate )) )
          latestdate = d;
      }
    }
    
    return latestdate;
  }
  
  public List<CourseInfo> getCourseInfoList()
  {
    ArrayList<CourseInfo> list = new ArrayList<>();
    list.addAll(coursebypkidmap.values() );
    return list;
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
  
  
  public void addBlackboardInfo() throws InterruptedException
  {
    ResourceLinkManager rlm = ContentSystemServiceExFactory.getInstance().getResourceLinkManager();

    List<CSResourceLinkWrapper> rawlinks = null;
    List<LinkInfo> links = null;
    for ( BlobInfo bi : blobs )
    {
      debuglogger.info( "Working on blob " + bi.getBlobId() );
      if (Thread.interrupted())
        throw new InterruptedException();
      
      for ( FileVersionInfo fvi : bi.getFileVersions() )
      {
        debuglogger.info( "    Working on file version " + fvi.getFileId() + " with course id " + fvi.getBbCourseId() );
        rawlinks = rlm.getResourceLinks( Long.toString( fvi.getFileId() ) + "_1" );
        debuglogger.info( "    Found " + rawlinks.size() + " links" );

        CourseInfo fileci = getCourseInfo( fvi.getBbCourseId() );  // also stores it in maps
        fvi.setCoursePkId( fileci.getCoursePkId() );
        fileci.addFile( fvi );
        
        links = new ArrayList<>();
        for ( CSResourceLinkWrapper rawlink : rawlinks )
        {
          debuglogger.info( "        Processing link " + rawlink.getCourseId() );
          CourseInfo link_courseinfo = getCourseInfo( rawlink );  // course pkid
          LinkInfo li = new LinkInfo( rawlink, link_courseinfo.getLastAccessed() );
          links.add( li );
          link_courseinfo.addLink( li );
          bi.addLastAccessed( link_courseinfo.getLastAccessed() );
        }
        
        addLinks( fvi.getStringFileId(), links );
      }      
    }
    sortBlobs();
    
  }
  
  public void addBuilders( CourseInfo ci )      
  {
    UserDbLoader userloader = null;
    Course course = null;
    try
    {
      course = CourseDbLoader.Default.getInstance().loadById( ci.getCoursePkId() );
      if ( course == null )
        return;
      userloader = UserDbLoader.Default.getInstance();
    }
    catch ( PersistenceException ex )
    {
      return;
    }
      
  
    ci.setTitle( course.getTitle() );

    ArrayList<Object> all = new ArrayList<>();
    CourseMembership.Role[] roles = { CourseMembership.Role.INSTRUCTOR, CourseMembership.Role.COURSE_BUILDER };
    for ( CourseMembership.Role role : roles  )
    {
      CourseMembershipSearch query = new CourseMembershipSearch( course.getId() );
      query.searchByRole( role.getIdentifier() );
      try {
        query.run();
      } catch (PersistenceException ex) {
        continue;
      }
      for ( Object o : query.getResults() )
        all.add( o );
    }
    
    BuilderInfo bi;
    for ( Object o : all )
    {
      if ( o instanceof CourseMembership )
      {
        CourseMembership cm = (CourseMembership)o;
        bi = buildermap.get( cm.getUserId().toExternalString() );
        if ( bi == null )
        {
          try
          {
            User user = userloader.loadById( cm.getUserId() );
            bi = new BuilderInfo( 
                            cm.getUserId().toExternalString(), 
                            user.getFamilyName(),
                            user.getGivenName() + " " + user.getFamilyName(),
                            user.getEmailAddress()
                    );
            buildermap.put( bi.getId(), bi );
            builders.add( bi );
          }
          catch ( PersistenceException knfex )
          {
            bi = new BuilderInfo( cm.getUserId().toExternalString(), null, null, null );              
          }
        }
        bi.addCourseInfo( ci );
      }
    }
    builders.sort( new Comparator<BuilderInfo>() {
      @Override
      public int compare( BuilderInfo ba, BuilderInfo bb )
      {
        return ba.familyname.compareTo( bb.familyname );
      }
    } );
  }
  
}
