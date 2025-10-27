package com.htshare.util;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates self-signed SSL certificates for HTTPS server */
public class SSLCertificateGenerator {
  private static final Logger logger = LoggerFactory.getLogger(SSLCertificateGenerator.class);

  public static final String KEYSTORE_PASSWORD = "fileshare123";
  private static final String ALIAS = "fileshare";
  private static final int VALIDITY_DAYS = 365;

  /** Generate a KeyStore with self-signed certificate */
  public static KeyStore generateKeyStore() throws Exception {
    logger.info("Generating self-signed SSL certificate...");

    // Generate RSA key pair
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048, new SecureRandom());
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    // Create self-signed certificate
    X509Certificate cert = generateCertificate(keyPair);

    // Create KeyStore and add certificate
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    keyStore.setKeyEntry(
        ALIAS, keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(), new Certificate[] {cert});

    logger.info("Self-signed certificate generated successfully");
    logger.info("Certificate CN: {}", cert.getSubjectX500Principal().getName());
    logger.info("Valid from: {} to: {}", cert.getNotBefore(), cert.getNotAfter());

    return keyStore;
  }

  /** Generate X509 self-signed certificate */
  private static X509Certificate generateCertificate(KeyPair keyPair) throws Exception {
    // Certificate information
    X500Principal subject = new X500Principal("CN=File Share Server, O=File Share, C=US");
    BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
    Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
    Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * VALIDITY_DAYS);

    // Use BouncyCastle or sun.security if available
    try {
      // Try using sun.security (built-in to JDK)
      return generateCertificateWithSunSecurity(keyPair, subject, serial, notBefore, notAfter);
    } catch (Exception e) {
      logger.warn("Could not use sun.security for certificate generation: {}", e.getMessage());
      throw new Exception("Certificate generation failed. Please ensure Java 8+ is installed.", e);
    }
  }

  /** Generate certificate using sun.security (JDK built-in) */
  @SuppressWarnings("restriction")
  private static X509Certificate generateCertificateWithSunSecurity(
      KeyPair keyPair, X500Principal subject, BigInteger serial, Date notBefore, Date notAfter)
      throws Exception {

    // Use reflection to access sun.security classes (works on JDK 8-17+)
    Class<?> certInfoClass = Class.forName("sun.security.x509.X509CertInfo");
    Class<?> certImplClass = Class.forName("sun.security.x509.X509CertImpl");
    Class<?> certSerialClass = Class.forName("sun.security.x509.CertificateSerialNumber");
    Class<?> certValidityClass = Class.forName("sun.security.x509.CertificateValidity");
    Class<?> certX500NameClass = Class.forName("sun.security.x509.X500Name");
    Class<?> algIdClass = Class.forName("sun.security.x509.AlgorithmId");
    Class<?> certVersionClass = Class.forName("sun.security.x509.CertificateVersion");
    Class<?> certAlgIdClass = Class.forName("sun.security.x509.CertificateAlgorithmId");

    // Create certificate info
    Object info = certInfoClass.getDeclaredConstructor().newInstance();

    // Set version (V3)
    Object version = certVersionClass.getField("V3").get(null);
    info.getClass().getMethod("set", String.class, Object.class).invoke(info, "version", version);

    // Set serial number
    Object serialNumber =
        certSerialClass.getDeclaredConstructor(BigInteger.class).newInstance(serial);
    info.getClass()
        .getMethod("set", String.class, Object.class)
        .invoke(info, "serialNumber", serialNumber);

    // Set validity
    Object validity =
        certValidityClass
            .getDeclaredConstructor(Date.class, Date.class)
            .newInstance(notBefore, notAfter);
    info.getClass().getMethod("set", String.class, Object.class).invoke(info, "validity", validity);

    // Set subject and issuer (same for self-signed)
    Object x500Name =
        certX500NameClass.getDeclaredConstructor(String.class).newInstance(subject.getName());
    info.getClass().getMethod("set", String.class, Object.class).invoke(info, "subject", x500Name);
    info.getClass().getMethod("set", String.class, Object.class).invoke(info, "issuer", x500Name);

    // Set public key
    info.getClass()
        .getMethod("set", String.class, Object.class)
        .invoke(info, "key", keyPair.getPublic());

    // Set signature algorithm
    Object algId = algIdClass.getMethod("get", String.class).invoke(null, "SHA256withRSA");
    Object certAlgId = certAlgIdClass.getDeclaredConstructor(algId.getClass()).newInstance(algId);
    info.getClass()
        .getMethod("set", String.class, Object.class)
        .invoke(info, "algorithmID", certAlgId);

    // Create and sign certificate
    Object cert = certImplClass.getDeclaredConstructor(certInfoClass).newInstance(info);
    cert.getClass()
        .getMethod("sign", PrivateKey.class, String.class)
        .invoke(cert, keyPair.getPrivate(), "SHA256withRSA");

    return (X509Certificate) cert;
  }
}
