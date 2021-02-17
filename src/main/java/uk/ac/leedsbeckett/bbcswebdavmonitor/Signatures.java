/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

/**
 *
 * @author jon
 */
public class Signatures
{
  public static final byte[] ZIP = { 0x50, 0x4B, 0x03, 0x04 };
  
  public static boolean equal( byte[] a, byte[] b )
  {
    if ( a==null || b==null || a.length != b.length )
      return false;
    for ( int i=0; i<a.length; i++ )
      if ( a[i] != b[i] )
        return false;
    return true;
  }
  
  public static boolean isZip( byte[] a )
  {
    return equal( ZIP, a );
  }
}
