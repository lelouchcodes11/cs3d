package android.net.nsd;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A service discovered or registered with DNS-SD (mDNS), same API as Android */
public final class NsdServiceInfo {
    private String mServiceName;
    private String mServiceType;
    private int mPort;
    private final List<InetAddress> mHostAddresses = new ArrayList<>();
    private final Map<String, byte[]> mTxtRecord = new HashMap<>();
    private String mHostname;

    public NsdServiceInfo() {
    }

    public String getServiceName() {
        return mServiceName;
    }

    public void setServiceName(String s) {
        mServiceName = s;
    }

    public String getServiceType() {
        return mServiceType;
    }

    public void setServiceType(String s) {
        mServiceType = s;
    }

    /** @deprecated use getHostAddresses */
    @Deprecated
    public InetAddress getHost() {
        return mHostAddresses.isEmpty() ? null : mHostAddresses.get(0);
    }

    /** @deprecated use setHostAddresses */
    @Deprecated
    public void setHost(InetAddress s) {
        mHostAddresses.clear();
        if (s != null) mHostAddresses.add(s);
    }

    public List<InetAddress> getHostAddresses() {
        return Collections.unmodifiableList(new ArrayList<>(mHostAddresses));
    }

    public void setHostAddresses(List<InetAddress> addresses) {
        mHostAddresses.clear();
        if (addresses != null) mHostAddresses.addAll(addresses);
    }

    public String getHostname() {
        return mHostname;
    }

    public void setHostname(String hostname) {
        mHostname = hostname;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int p) {
        mPort = p;
    }

    public void setAttribute(String key, String value) {
        mTxtRecord.put(key, value == null ? null : value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void removeAttribute(String key) {
        mTxtRecord.remove(key);
    }

    public Map<String, byte[]> getAttributes() {
        return Collections.unmodifiableMap(mTxtRecord);
    }

    @Override
    public String toString() {
        return "name: " + mServiceName + ", type: " + mServiceType + ", hostAddresses: " + mHostAddresses + ", port: " + mPort;
    }
}
