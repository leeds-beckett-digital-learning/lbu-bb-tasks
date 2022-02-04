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
public class BlobSearchResult
{
  public long id;
  public Timestamp created;
  public int storagestate;
  public int refcount;
  public String storagefilename;
  public String tempstoragefilename;
  
  public byte[] signature;
  public boolean analysed=false;
  public long size;
  public long binsize;
  public boolean iszip;
  public long turnitinusage;
  public long csfilesusage;
  public long totalusage;
  
  public StringBuilder message = new StringBuilder();
}
