/*
 * Copyright 2022 Leeds Beckett University.
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
