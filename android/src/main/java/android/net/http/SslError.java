package android.net.http;

public class SslError {
    public static final int SSL_NOTYETVALID = 0;
    public static final int SSL_EXPIRED = 1;
    public static final int SSL_IDMISMATCH = 2;
    public static final int SSL_UNTRUSTED = 3;
    public static final int SSL_DATE_INVALID = 4;
    public static final int SSL_INVALID = 5;

    private final int mError;
    private final SslCertificate mCertificate;
    private final String mUrl;

    public SslError(int error, SslCertificate certificate, String url) {
        mError = error;
        mCertificate = certificate;
        mUrl = url;
    }

    public SslCertificate getCertificate() {
        return mCertificate;
    }

    public String getUrl() {
        return mUrl;
    }

    public boolean addError(int error) {
        return true;
    }

    public boolean hasError(int error) {
        return error == mError;
    }

    public int getPrimaryError() {
        return mError;
    }

    @Override
    public String toString() {
        return "primary error: " + mError + " url: " + mUrl;
    }
}
