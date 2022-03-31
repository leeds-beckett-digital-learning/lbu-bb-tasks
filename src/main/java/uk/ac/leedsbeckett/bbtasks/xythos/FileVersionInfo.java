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
  final String strfileid;

  public FileVersionInfo( String path, long blobid, long size, long fileid )
  {
    this.path   = path;
    this.blobid = blobid;
    this.size   = size;
    this.fileid = fileid;
    this.strfileid = Long.toString( fileid ) + "_1";
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

  public String getStringFileId()
  {
    return strfileid;
  }
}
