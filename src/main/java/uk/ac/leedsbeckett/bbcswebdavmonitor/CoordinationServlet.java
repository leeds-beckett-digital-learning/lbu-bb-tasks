/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uk.ac.leedsbeckett.bbcswebdavmonitor.messaging.InterserverMessage;

/**
 *
 * @author jon
 */
@WebServlet("/coordination")
public class CoordinationServlet extends AbstractServlet
{
  /**
   * Works out which page of information to present and calls the appropriate
   * method.
   * 
   * @param req The request data.
   * @param resp The response data
   * @throws ServletException
   * @throws IOException 
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"index.html\">Home</a></p>" );      
      out.println( "<h1>Coordination Servlet</h1>" );
      out.println( "<p>Automated processes should only use POST method." );
      out.println( "</body></html>" );
    }
    
  }  

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    Object obj=null;
    bbmonitor.logger.debug( "Client has called coordination server. PUT Client = " + req.getRemoteHost() + " Server = " + req.getLocalName() );
    try ( ObjectInputStream objin = new ObjectInputStream( req.getInputStream() ) )
    {
      obj = objin.readObject();
      bbmonitor.logger.debug( "Received object of class " + obj.getClass().getName() );
    }
    catch ( Exception e )
    {
      bbmonitor.logger.error( "Error attempting to deserialize incoming java object.", e );
      resp.sendError( 500, "Error attempting to deserialize incoming java object." );
      return;
    }
    resp.setStatus( 200 );
    
    if ( obj != null && obj instanceof InterserverMessage )
      servercoordinator.handleMessage( (InterserverMessage)obj );
  }
  
  
}
