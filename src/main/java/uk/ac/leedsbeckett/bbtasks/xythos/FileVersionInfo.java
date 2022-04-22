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
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.persist.Id;

/**
 *
 * @author maber01
 */
public class FileVersionInfo
{
  final String path;
  final long blobid;
  final long size;
  final long fileid;
  final String digest;
  final String strfileid;
  final String bbcourseid;
  
  Id coursepkid;
  String copyurl;
  
  public FileVersionInfo( String path, long blobid, long size, long fileid, String digest )
  {
    this.path   = path;
    this.blobid = blobid;
    this.size   = size;
    this.fileid = fileid;
    this.digest = digest;
    this.strfileid = Long.toString( fileid ) + "_1";
    this.bbcourseid = ( path.startsWith("/courses/") )?path.split("/")[2]:null;
  }

  public String getPath()
  {
    return path;
  }

  public long getBlobId()
  {
    return blobid;
  }

  public long getSize()
  {
    return size;
  }

  public long getFileId()
  {
    return fileid;
  }

  public String getDigest()
  {
    return digest;
  }
  
  public String getStringFileId()
  {
    return strfileid;
  }

  public String getBbCourseId()
  {
    return bbcourseid;
  }

  public Id getCoursePkId() {
    return coursepkid;
  }

  public void setCoursePkId(Id coursepkid) {
    this.coursepkid = coursepkid;
  }
  
  public String getCopyUrl() {
    return copyurl;
  }

  public void setCopyURL(String copyurl) {
    this.copyurl = copyurl;
  }
  
  
}
