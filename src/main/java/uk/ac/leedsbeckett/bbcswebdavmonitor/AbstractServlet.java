/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author jon
 */
public abstract class AbstractServlet extends HttpServlet
{
  BBMonitor bbmonitor;
  ServerCoordinator servercoordinator;
  
  //Thread currenttask=null;  
  
  
  /**
   * Get a reference to the right instance of BBMonitor from an attribute which
   * that instance put in the servlet context.
  */
  @Override
  public void init() throws ServletException
  {
    super.init();
    bbmonitor = (BBMonitor)getServletContext().getAttribute( BBMonitor.ATTRIBUTE_CONTEXTBBMONITOR );
    servercoordinator = bbmonitor.servercoordinator;
  }
  
  public void sendError( HttpServletRequest req, HttpServletResponse resp, String error ) throws ServletException, IOException
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
      out.println( "<body><p>" );
      out.println( error );
      out.println( "</p></body></html>" );
    }  
  }

}
