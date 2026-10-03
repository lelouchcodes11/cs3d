package android.net.http;

import java.security.cert.X509Certificate;

public class SslCertificate {
    private final X509Certificate mCert;

    public SslCertificate(X509Certificate certificate) {
        mCert = certificate;
    }

    public X509Certificate getX509Certificate() {
        return mCert;
    }

    @Override
    public String toString() {
        return mCert == null ? "SslCertificate" : mCert.getSubjectX500Principal().getName();
    }
}
