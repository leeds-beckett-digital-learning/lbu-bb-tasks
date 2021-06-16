/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leedsbeckett.bbcswebdavmonitor.messaging;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.log4j.Logger;

/**
 *
 * @author jon
 */
public class MessageDispatcher
{
  Logger logger;
  String url;
        
  public MessageDispatcher( String url, Logger logger)
  {
    this.url    = url;
    this.logger = logger;
  }

  public void dispatch( InterserverMessage message )
  {
    String fullurl = "https://" + message.getRecipientId() + url;
    try
    {
      SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial( new LocalTrustStrategy() ).build();
      SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
              sslcontext, null, null, new LocalHostnameVerifier() );
      CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

      try
      {
        logger.debug("Putting to " + fullurl );
        HttpPut httpput = new HttpPut( fullurl );
        httpput.setEntity( EntityBuilder.create().setSerializable( message ).build() );
        logger.debug("Executing request " + httpput.getRequestLine());
        CloseableHttpResponse response = httpclient.execute(httpput);
        try
        {
          HttpEntity entity = response.getEntity();
          logger.debug( "Status: " + response.getStatusLine() );
          entity.getContent().close();
        } finally
        {
          response.close();
        }
      } finally
      {
        httpclient.close();
      }
    } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex)
    {
      logger.error("Error trying to contact peer server.", ex);
    }
  }

  
  class LocalTrustStrategy implements TrustStrategy
  {
    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
      return true;
    }
  }
  
  class LocalHostnameVerifier implements HostnameVerifier
  {
    @Override
    public boolean verify(String hostname, SSLSession session)
    {
      return true;
    }
  }
}
