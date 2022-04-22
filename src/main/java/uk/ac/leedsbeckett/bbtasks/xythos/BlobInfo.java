/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import java.util.ArrayList;
import java.util.Date;

/**
 *
 * @author jon
 */
public class BlobInfo
{
  final long blobid;
  final long size;
  final String digest;
  
  int linkcount = 0;
  ArrayList<FileVersionInfo> fileversions = new ArrayList<>(); 
  Date lastaccessed=null;
        
  public BlobInfo(long blobid, long size, String digest) {
    this.blobid = blobid;
    this.size   = size;
    this.digest = digest;
  }

  public long getBlobId() {
    return blobid;
  }

  public long getSize() {
    return size;
  }

  public String getDigest() {
    return digest;
  }

  public int getLinkCount() {
    return linkcount;
  }

  public void setLinkCount(int linkcount) {
    this.linkcount = linkcount;
  }

  public ArrayList<FileVersionInfo> getFileVersions() {
    return fileversions;
  }
  
  public void addLastAccessed( Date date )
  {
    if ( lastaccessed == null )
      lastaccessed = date;
    else if ( date != null && date.after( lastaccessed ) )
      lastaccessed = date;
  }
  
  public Date getLastAccessed()
  {
    return lastaccessed;
  }
}
