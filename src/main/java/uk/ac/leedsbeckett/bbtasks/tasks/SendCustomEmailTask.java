/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.tasks;

import blackboard.data.registry.SystemRegistryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jon
 */
public class SendCustomEmailTask extends BaseTask
{
  public String boilerplate;
  
  private transient InternetAddress me;
  
  @JsonCreator
  public SendCustomEmailTask( @JsonProperty("boilerplate") String boilerplate )
  {
    this.boilerplate = boilerplate;
  }
  
  
  @Override
  public void doTask() throws InterruptedException
  {
    String emailaddress = webappcore.getProperties().getEmailAddress();
    String emailname = webappcore.getProperties().getEmailName();
    if ( emailaddress == null || 
         emailname == null || 
         emailaddress.isBlank() ||
         emailname.isBlank() )
    {
      debuglogger.error( "Cannot send emails - no configured 'from' address." );      
      return;
    }
    
    try
    {
      me = new InternetAddress( emailaddress, emailname );
    }
    catch (UnsupportedEncodingException ex)
    {
      debuglogger.error( "Error composing email address.", ex );
      return;
    }
    
    
    Path emailfile = webappcore.pluginbase.resolve( "mailjob.txt" );
    boolean inemail=false;
    String line = null;
    String name = null, address = null, subject= null;
    StringBuilder body = new StringBuilder();
    
    try ( BufferedReader reader = new BufferedReader( new FileReader( emailfile.toFile() ) ) )
    {
      while ( (line = reader.readLine()) != null )
      {
        if ( Thread.interrupted() ) throw new InterruptedException();        
        if ( !inemail )
        {
          if ( line.startsWith( "----Start Email" ) )
          {
            inemail = true;
            name = null;
            address = null;
            subject = null;
            body.setLength(0);
            body.append( "<div>\n" );
            body.append( boilerplate );
          }
          continue;
        }
        
        
        if ( line.startsWith( ":" ) )
        {
          if ( line.startsWith( ":Name:" ) )
            name = line.substring( 6 );
          else if ( line.startsWith( ":Address:" ) )
            address = line.substring( 9 );
          else if ( line.startsWith( ":Subject:" ) )
            subject = line.substring( 9 );
        }
        else if ( line.startsWith( "----End Email" ) )
        {
          inemail = false;
          body.append( "</div>\n" );
          processOneEmail( name, address, subject, body.toString() );
        }
        else
        {
          body.append( line );
          body.append( "\n" );
        }
        
      }
    }
    catch (IOException ex)
    {
      debuglogger.error( "Error reading email job file. ", ex );
    }

  }
  
  
  void processOneEmail( String name, String address, String subject, String body )
  {
    debuglogger.info( "Sending Email " + name + " " + address + " " + subject  );
    //debuglogger.info( body );
    
    InternetAddress[] recipients = new InternetAddress[1];
    InternetAddress[] ccs = new InternetAddress[1];
    InternetAddress from;
    try
    {
      from          = me;
      recipients[0] = new InternetAddress( address, name );
      ccs[0]        = me;
      sendHtmlEmail( subject, from, new InternetAddress[0], recipients, ccs, body );
    }
    catch (UnsupportedEncodingException ex)
    {
      debuglogger.error( "Error composing email address.", ex );
    }
    catch (MessagingException ex)
    {
      debuglogger.error( "Error sending email.", ex );
    }
  }
  
  
  public void sendHtmlEmail(
          String subject, 
          InternetAddress from, 
          InternetAddress[] reply, 
          InternetAddress[] recipients, 
          InternetAddress[] courtesycopies, 
          String message) throws MessagingException
  {
    MimeMessage email = getBbEmail();
    MimeMultipart multipart = new MimeMultipart();
    BodyPart messageBodyPart = new MimeBodyPart();

    email.setSubject(subject);
    if ( reply != null && reply.length > 0 )
      email.setReplyTo( reply );
    email.setFrom( from );
    messageBodyPart.setContent(message, "text/html");
    multipart.addBodyPart(messageBodyPart);
    email.setRecipients( javax.mail.Message.RecipientType.TO, recipients );
    if ( courtesycopies != null && courtesycopies.length > 0 )
      email.setRecipients( javax.mail.Message.RecipientType.CC, courtesycopies );
    email.setContent(multipart);
    Transport.send(email);
  }  

  
  MimeMessage getBbEmail()
  {
    Properties bbprops = blackboard.platform.config.ConfigurationServiceFactory.getInstance().getBbProperties();
    String smtpHost = bbprops.getProperty("bbconfig.smtpserver.hostname");
    String dBsmtpHost = SystemRegistryUtil.getString("smtpserver_hostname", smtpHost);
    if (!StringUtils.isEmpty( dBsmtpHost ) && !"0.0.0.0".equals( dBsmtpHost ) )
      smtpHost = dBsmtpHost;
    if ( debuglogger != null ) debuglogger.debug( "Using " + smtpHost );
    
    Properties mailprops = new Properties();
    mailprops.setProperty("mail.smtp.host", smtpHost);
    Session mailSession = Session.getDefaultInstance(mailprops);
    
    return new MimeMessage(mailSession);
  }  
  

}
