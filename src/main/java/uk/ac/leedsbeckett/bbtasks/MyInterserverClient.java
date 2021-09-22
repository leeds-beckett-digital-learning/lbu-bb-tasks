/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbtasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xythos.security.api.Context;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import org.apache.log4j.Logger;
import uk.ac.leedsbeckett.bbb2utils.coordination.InterserverCoordinationClient;
import uk.ac.leedsbeckett.bbb2utils.json.JsonConvertor;
import uk.ac.leedsbeckett.bbb2utils.messaging.MessageHeader;
import uk.ac.leedsbeckett.bbb2utils.messaging.MessageUtils;
import uk.ac.leedsbeckett.bbtasks.messaging.CoordinatorClientMessageListener;
import uk.ac.leedsbeckett.bbtasks.messaging.InterserverMessage;
import uk.ac.leedsbeckett.bbtasks.messaging.MessageUnion;


/**
 *
 * @author jon
 */
public class MyInterserverClient extends InterserverCoordinationClient
{
  JsonConvertor<MessageUnion> mujsonconv = new JsonConvertor<>(MessageUnion.class);

  final ArrayList<CoordinatorClientMessageListener> listeners = new ArrayList<>();
  
  public MyInterserverClient( Logger logger )
  {
    super( logger );
  }
  
  @Override
  public boolean processMessageHeader(MessageHeader header)
  {
    if ( header.contentType != MessageHeader.ContentType.JSON )
      return true;

    if ( header.canonicalClassName.equals( MessageUnion.class.getCanonicalName() ) )
      return true;        
    
    return false;
  }

  @Override
  public void processMessageContent(MessageHeader header, byte[] content)
  {
    try
    {
      String json = new String( content, "UTF-8" );
      getLogger().info( " Received message:\n[" + json + "]" );
      MessageUnion union = mujsonconv.read(json);
      InterserverMessage message = union.get();      
      notifyListeners( header, message );
    }
    catch ( UnsupportedEncodingException | JsonProcessingException ex )
    {
    }
  }

  public boolean sendMessage( InterserverMessage message )
  {
    return sendMessage( message, "*" );
  }
  
  public boolean sendMessage( InterserverMessage message, String toserverid )
  {
    Context context = null;
    MessageUnion union = new MessageUnion();
    union.set( message );
    MessageHeader header = new MessageHeader();
    header.contentType = MessageHeader.ContentType.JSON;
    header.canonicalClassName = message.getClass().getCanonicalName();
    header.fromPlugin = getPluginId();
    header.fromServer = getServerId();
    header.toPlugin = getPluginId();
    header.toServer = toserverid;
    
    try
    {
      String id = MessageUtils.createMessageId();
      String mujson = mujsonconv.write( union );
      byte[] content = mujson.getBytes("UTF-8");
      return sendMessage( header, content );
    }
    catch ( Exception ex )
    {
      getLogger().error( "Exception sending message.", ex );
      return false;
    }
  }
  
  public void addListener( CoordinatorClientMessageListener listener )
  {
    if ( !listeners.contains( listener ) )
      listeners.add( listener );
  }
  
  public void removeListener( CoordinatorClientMessageListener listener )
  {
    if ( listeners.contains( listener ) )
      listeners.remove( listener );    
  }
  
  void notifyListeners( MessageHeader header, InterserverMessage message )
  {
    for ( CoordinatorClientMessageListener listener : listeners )
      listener.receiveMessage( header, message );
  }
}
