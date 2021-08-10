/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

import java.sql.Timestamp;

/**
 *
 * @author jon
 */
public class VideoSearchResult
{
  public long fileid;
  public long blobid;
  public String etag;
  public String analysedetag;
  public String path;
  public int created;
  public String recordedmimetype;
  public String detectedmimetype;
  public int refcount;
  
  public boolean analysed=false;
  public long size;
  
  public String medialog;
  
  public long duration=-1L;
  public long datarate;
  
  public String toString()
  {
    StringBuilder s = new StringBuilder();
    
    s.append(blobid );
    s.append( "," );
    appendQuoted( s, etag, true );
    appendQuoted( s, analysedetag, true );
    appendQuoted( s, path, true );
    appendQuoted( s, recordedmimetype, true );
    appendQuoted( s, detectedmimetype, true );
    s.append( size );
    s.append( "," );
    s.append( duration );
    s.append( "," );
    s.append( datarate );
    s.append( "\n"   );
    
    return s.toString();
  }
  
  public static void appendQuoted( StringBuilder sb, String s, boolean comma )
  {
    sb.append( "\"" );
    if ( s != null )
      sb.append( s.replace( "\"", "\"\"" ) );
    sb.append( "\"" );
    if ( comma ) sb.append( "," );
  }
}
