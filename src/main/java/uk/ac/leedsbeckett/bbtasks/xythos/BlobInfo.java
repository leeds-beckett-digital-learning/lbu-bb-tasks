/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import java.util.ArrayList;

/**
 *
 * @author jon
 */
public class BlobInfo
{
  final long blobid;
  final long size;
  int linkcount = 0;
  ArrayList<FileVersionInfo> fileversions = new ArrayList<>(); 
        
  public BlobInfo(long blobid, long size) {
    this.blobid = blobid;
    this.size = size;
  }

  public long getBlobId() {
    return blobid;
  }

  public long getSize() {
    return size;
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
  
}
