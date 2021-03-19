/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor;

import blackboard.data.registry.SystemRegistryUtil;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jon
 */
public class EMailSender
{

  public static MimeMessage getBbEmail()
  {
    Properties bbprops = blackboard.platform.config.ConfigurationServiceFactory.getInstance().getBbProperties();
    String smtpHost = bbprops.getProperty("bbconfig.smtpserver.hostname");

    String dBsmtpHost = SystemRegistryUtil.getString("smtpserver_hostname", smtpHost);

    if (!StringUtils.isEmpty(dBsmtpHost))
    {
      smtpHost = dBsmtpHost;
    }

    Properties mailprops = System.getProperties();
    mailprops.setProperty("mail.smtp.host", smtpHost);
    Session mailSession = Session.getDefaultInstance(mailprops);

    return new MimeMessage(mailSession);
  }

  public static void sendPlainEmail(
          String subject, 
          InternetAddress from, 
          InternetAddress[] reply, 
          InternetAddress[] recipients, 
          InternetAddress[] courtesycopies, 
          String message ) throws MessagingException
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
    email.setRecipients( Message.RecipientType.TO, recipients );
    if ( courtesycopies != null && courtesycopies.length > 0 )
      email.setRecipients( Message.RecipientType.CC, courtesycopies );
    email.setContent(multipart);
    Transport.send(email);
  }
  
  public static void sendTestMessage() throws UnsupportedEncodingException, MessagingException
  {
    InternetAddress dl = new InternetAddress( "digitallearning@leedsbeckett.ac.uk" );
    dl.setPersonal( "Digital Learning Service" );
    InternetAddress jm = new InternetAddress( "j.r.maber@leedsbeckett.ac.uk" );
    jm.setPersonal( "Jon Maber" );
    
    InternetAddress[] recipients = { jm, dl };
    sendPlainEmail( "Test Message from Blackboard Building Block.", dl, null, recipients, null, "Hello Jon." );
  } 
}
