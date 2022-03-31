/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.persist.Id;
import blackboard.platform.contentsystem.data.CSResourceLinkWrapper;
import java.util.ArrayList;
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
}
