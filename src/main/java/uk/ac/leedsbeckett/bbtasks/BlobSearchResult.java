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
