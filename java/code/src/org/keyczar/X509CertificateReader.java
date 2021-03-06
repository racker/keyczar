// Copyright 2011 Google Inc. All Rights Reserved.

package org.keyczar;

import org.keyczar.RsaPublicKey.Padding;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.enums.KeyStatus;
import org.keyczar.enums.KeyType;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.i18n.Messages;
import org.keyczar.interfaces.KeyczarReader;

import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;

/**
 * @author swillden@google.com (Shawn Willden)
 */
public class X509CertificateReader implements KeyczarReader {
  private final InputStream certificateStream;
  private final KeyPurpose purpose;
  private final Padding padding;
  private KeyMetadata meta = null;
  private KeyczarPublicKey key;

  /**
   * Creates an certificate reader that reads a key from the specified stream, tags it with the
   * specified purpose and sets it to use the specified padding.
   *
   * @param padding The padding to associate with the key.  May be null for RSA keys, in
   * which case it will default to OAEP.  Must be null for DSA keys.
   * @throws KeyczarException
   */
  public X509CertificateReader(KeyPurpose purpose, InputStream certificateStream, Padding padding)
      throws KeyczarException {
    if (purpose == null) {
      throw new KeyczarException("X509Certificate purpose must not be null");
	}
	if (certificateStream == null) {
	  throw new KeyczarException("X509Certificate stream must not be null");
	}
    this.purpose = purpose;
    this.certificateStream = certificateStream;
    this.padding = padding;
  }

  @Override
  public String getKey(int version) throws KeyczarException {
    ensureCertificateRead();
    return key.toString();
  }

  @Override
  public String getKey() throws KeyczarException {
    ensureCertificateRead();
    return key.toString();
  }

  @Override
  public String getMetadata() throws KeyczarException {
    ensureCertificateRead();
    return meta.toString();
  }

  private void ensureCertificateRead() throws KeyczarException {
    if (key == null) {
      try {
        parseCertificate();
        constructMetadata();
      } catch (CertificateException e) {
        throw new KeyczarException(Messages.getString("KeyczarTool.InvalidCertificate"));
      }
    }
  }

  private void constructMetadata() throws KeyczarException {
    if (purpose == KeyPurpose.ENCRYPT && key.getType() == KeyType.DSA_PUB) {
      throw new KeyczarException(Messages.getString("Keyczartool.InvalidUseOfDsaKey"));
    }
    meta = new KeyMetadata("imported from certificate", purpose, key.getType());
    meta.addVersion(new KeyVersion(1, KeyStatus.PRIMARY, true /* exportable */));
  }

  private void parseCertificate() throws CertificateException, KeyczarException {
    Certificate certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(certificateStream);
    PublicKey publicKey = certificate.getPublicKey();

    if (publicKey instanceof RSAPublicKey) {
      key = readRsaX509Certificate(publicKey, padding);
    } else if (publicKey instanceof DSAPublicKey) {
      if (padding != null) {
        throw new KeyczarException(Messages.getString("InvalidPadding", padding.name()));
      }
      key = readDsaX509Certificate(publicKey);
    } else {
      throw new KeyczarException("Unrecognized key type " + publicKey.getAlgorithm() +
          " in certificate");
    }
  }

  private static DsaPublicKey readDsaX509Certificate(PublicKey publicKey) throws KeyczarException {
    DSAPublicKey jcePublicKey = (DSAPublicKey) publicKey;
    DsaPublicKey key = new DsaPublicKey();
    key.set(jcePublicKey.getY(), jcePublicKey.getParams().getP(), jcePublicKey.getParams().getQ(),
        jcePublicKey.getParams().getG());
    return key;
  }

  private static RsaPublicKey readRsaX509Certificate(PublicKey publicKey, Padding padding)
      throws KeyczarException {
    RSAPublicKey jceKey = (RSAPublicKey) publicKey;
    RsaPublicKey key = new RsaPublicKey();
    key.set(jceKey.getModulus().bitLength(), jceKey.getModulus(), jceKey.getPublicExponent());
    key.setPadding(padding == null ? Padding.OAEP : padding);
    return key;
  }
}
