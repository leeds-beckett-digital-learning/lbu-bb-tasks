/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;


import com.xythos.fileSystem.File;
import com.xythos.fileSystem.Revision;
import com.xythos.fileSystem.BinaryObject;
import com.xythos.common.BinaryObjectStorage;
import com.xythos.common.InternalException;
import com.xythos.fileSystem.ByteWritingException;
import com.xythos.storageServer.api.StorageServerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.logging.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author jon
 */
public class XythosAdapterChannel implements SeekableByteChannel
{
  public static final int MAX_CACHE_SIZE = 1024*1024;
  
  public Logger logger = null;
  
  private Revision revision = null;
  private BinaryObject blob = null;
  
  private boolean open      = true;
  private long    position  = 0L;
  private long    size      = 0L;
  private ByteArrayOutputStream baos = new ByteArrayOutputStream( MAX_CACHE_SIZE );
  
  long cacheposition = 0;
  byte[] cache       = null;
  
  
  public XythosAdapterChannel( Revision revision ) throws IOException
  {
    if ( revision == null )
      throw new IOException( "Null revision not allowed." );
    this.revision = revision;
    try
    {
      size = revision.getSize();
    } catch (StorageServerException ex)
    {
      throw new IOException( "Storage server exception.", ex );
    }
  }
  
  public XythosAdapterChannel( BinaryObject blob ) throws IOException
  {
    if ( blob == null )
      throw new IOException( "Null blob not allowed." );
    this.blob = blob;
    this.size = blob.getSize();
  }

  public void setLogger( Logger logger )
  {
    this.logger = logger;
  }
  
  @Override
  public int read( ByteBuffer bb ) throws IOException
  {
    int bytesrequested = bb.remaining();
    
    if ( !open )
      throw new IOException( "Attempt to read after closing." );
    
    if ( logger != null )
      logger.info( "Channel, requesting " + bytesrequested + " bytes." );

    // beyond end of file? indicate by returning -1
    if ( position >= size )
      return -1;
    
    // is the full amount NOT in the cache?
    if (
         cache == null ||                                  // no cache at all
         position < cacheposition ||                       // requested data before cached data
         position >= (cacheposition+(long)cache.length)    // start of requested data beyond cache
       )
    {
      long l;  // how much to read (Often read more than was requested)
      if ( size <= MAX_CACHE_SIZE )
      {
        // small file - read all of it from start
        cacheposition = 0;
        l = size;
      }
      else if ( (size-position) < MAX_CACHE_SIZE )
      {
        // requesting near end of file - back off and read more
        cacheposition = size-MAX_CACHE_SIZE;
        l = MAX_CACHE_SIZE;
      }
      else
      {
        // usual situation - read full cache from start point of the request
        cacheposition = position;
        l = MAX_CACHE_SIZE;
      }
      if ( logger != null )
        logger.info( "Channel, attempting to read " + l + " bytes starting from " + cacheposition );
    
      baos.reset();
      try
      {
        if ( revision != null )
          revision.getBytesWithoutClosing(
                baos,                  // write bytes here
                cacheposition,         // read starting from here
                l,                     // read this many bytes
                false,                 // do not commit
                false,                 // do not limit bandwidth
                true,                  // use cache
                false                  // do not log
          );
        else
          blob.getBytes( baos, cacheposition, l, false, false, false, false, null, null, null, true);
      }
      catch (StorageServerException ex)
      {
        throw new IOException( "Storage Server Exception.", ex );
      }
      
      cache = baos.toByteArray();
    }

    if ( logger != null )
    {
      logger.info( "Channel, reading from the cache. offset = " + (int)(position-cacheposition) + " size = " + bb.remaining() );
      // now get the data from the cache
      StringBuilder sb = new StringBuilder();
      for ( int i=0; i<bytesrequested; i++ )
      {
        sb.append( Integer.toHexString( cache[ i + (int)(position-cacheposition) ] ) );
        sb.append( " " );
      }
      logger.info( sb );
    }
    
    long end   = position-cacheposition+bytesrequested;
    int count = bytesrequested;
    if ( end >= cache.length )
      count = (int)(cache.length - (position-cacheposition));
    bb.put( cache, (int)(position-cacheposition), count );
    position += count;
    return count;
  }

  
  
  @Override
  public int write(ByteBuffer arg0) throws IOException
  {
    throw new UnsupportedOperationException("Not supported.");
  }

  @Override
  public long position() throws IOException
  {
    if ( !open )
      throw new IOException( "Attempt to read position after closing." );
    return position;
  }

  @Override
  public SeekableByteChannel position( long pos ) throws IOException
  {
    position = pos;
    if ( logger != null )
      logger.info( "Channel, setting position to " + pos );
    return this;
  }

  @Override
  public long size() throws IOException
  {
    try
    {
      if ( revision != null )
        return revision.getSize();
      return blob.getSize();
    }
    catch (StorageServerException ex)
    {
      throw new IOException( "Storage server exception.", ex );
    }
  }

  @Override
  public SeekableByteChannel truncate(long arg0) throws IOException
  {
    throw new UnsupportedOperationException("Not supported.");
  }

  @Override
  public boolean isOpen()
  {
    return open;
  }

  @Override
  public void close() throws IOException
  {
    if ( open )
    {
      try {
        open = false;
        if ( revision != null )
          revision.closeAfterRepeatedReads();
      } catch (StorageServerException ex) {
        throw new IOException( "Storage Server Exception", ex );
      }
    }
  }
    
}
