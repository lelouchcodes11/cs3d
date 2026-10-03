package android.net.nsd;

import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/**
 * Network service discovery (DNS-SD over mDNS) with the Android API, implemented with JmDNS on all
 * network interfaces.
 */
public final class NsdManager {
    private static final String TAG = "NsdManager";

    public static final int PROTOCOL_DNS_SD = 0x0001;
    public static final int FAILURE_INTERNAL_ERROR = 0;
    public static final int FAILURE_ALREADY_ACTIVE = 3;
    public static final int FAILURE_MAX_LIMIT = 4;
    public static final int FAILURE_OPERATION_NOT_RUNNING = 5;
    public static final int FAILURE_BAD_PARAMETERS = 6;

    private static volatile JmmDNS sJmmDns;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nsd");
        t.setDaemon(true);
        return t;
    });

    private final Map<RegistrationListener, ServiceInfo> mRegistrations = new HashMap<>();
    private final Map<DiscoveryListener, Object[]> mDiscoveries = new HashMap<>();
    private final Map<ServiceInfoCallback, Object[]> mInfoCallbacks = new HashMap<>();

    private static JmmDNS dns() {
        JmmDNS d = sJmmDns;
        if (d == null) {
            synchronized (NsdManager.class) {
                if (sJmmDns == null) sJmmDns = JmmDNS.Factory.getInstance();
                d = sJmmDns;
            }
        }
        return d;
    }

    /** "_fcast._tcp" -> "_fcast._tcp.local." */
    private static String fqType(String type) {
        String t = type.endsWith(".") ? type : type + ".";
        return t.endsWith(".local.") ? t : t + "local.";
    }

    private static NsdServiceInfo toNsd(ServiceInfo info) {
        NsdServiceInfo out = new NsdServiceInfo();
        out.setServiceName(info.getName());
        String type = info.getType();
        out.setServiceType(type.endsWith("local.") ? type.substring(0, type.length() - "local.".length()) : type);
        out.setPort(info.getPort());
        List<InetAddress> addresses = new ArrayList<>(Arrays.asList(info.getInet4Addresses()));
        addresses.addAll(Arrays.asList(info.getInet6Addresses()));
        out.setHostAddresses(addresses);
        out.setHostname(info.getServer());
        for (java.util.Enumeration<String> e = info.getPropertyNames(); e.hasMoreElements(); ) {
            String key = e.nextElement();
            out.setAttribute(key, info.getPropertyString(key));
        }
        return out;
    }

    public void registerService(NsdServiceInfo serviceInfo, int protocolType, RegistrationListener listener) {
        EXECUTOR.execute(() -> {
            try {
                Map<String, String> props = new HashMap<>();
                for (Map.Entry<String, byte[]> e : serviceInfo.getAttributes().entrySet()) {
                    props.put(e.getKey(), e.getValue() == null ? null : new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
                }
                ServiceInfo info = ServiceInfo.create(fqType(serviceInfo.getServiceType()), serviceInfo.getServiceName(), serviceInfo.getPort(), 0, 0, props);
                dns().registerService(info);
                synchronized (mRegistrations) {
                    mRegistrations.put(listener, info);
                }
                listener.onServiceRegistered(serviceInfo);
            } catch (Throwable t) {
                Log.e(TAG, "registerService failed: " + t);
                listener.onRegistrationFailed(serviceInfo, FAILURE_INTERNAL_ERROR);
            }
        });
    }

    public void unregisterService(RegistrationListener listener) {
        ServiceInfo info;
        synchronized (mRegistrations) {
            info = mRegistrations.remove(listener);
        }
        if (info == null) return;
        EXECUTOR.execute(() -> {
            try {
                dns().unregisterService(info);
                listener.onServiceUnregistered(toNsd(info));
            } catch (Throwable t) {
                listener.onUnregistrationFailed(toNsd(info), FAILURE_INTERNAL_ERROR);
            }
        });
    }

    public void discoverServices(String serviceType, int protocolType, DiscoveryListener listener) {
        final String type = fqType(serviceType);
        ServiceListener sl = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                listener.onServiceFound(toNsd(event.getInfo()));
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                listener.onServiceLost(toNsd(event.getInfo()));
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
            }
        };
        synchronized (mDiscoveries) {
            mDiscoveries.put(listener, new Object[]{type, sl});
        }
        EXECUTOR.execute(() -> {
            try {
                dns().addServiceListener(type, sl);
                listener.onDiscoveryStarted(serviceType);
            } catch (Throwable t) {
                Log.e(TAG, "discoverServices failed: " + t);
                listener.onStartDiscoveryFailed(serviceType, FAILURE_INTERNAL_ERROR);
            }
        });
    }

    public void stopServiceDiscovery(DiscoveryListener listener) {
        Object[] d;
        synchronized (mDiscoveries) {
            d = mDiscoveries.remove(listener);
        }
        if (d == null) return;
        EXECUTOR.execute(() -> {
            dns().removeServiceListener((String) d[0], (ServiceListener) d[1]);
            listener.onDiscoveryStopped((String) d[0]);
        });
    }

    @Deprecated
    public void resolveService(NsdServiceInfo serviceInfo, ResolveListener listener) {
        EXECUTOR.execute(() -> {
            try {
                ServiceInfo[] infos = dns().getServiceInfos(fqType(serviceInfo.getServiceType()), serviceInfo.getServiceName(), 6000);
                for (ServiceInfo info : infos) {
                    if (info != null && info.getInet4Addresses().length + info.getInet6Addresses().length > 0) {
                        listener.onServiceResolved(toNsd(info));
                        return;
                    }
                }
                listener.onResolveFailed(serviceInfo, FAILURE_INTERNAL_ERROR);
            } catch (Throwable t) {
                listener.onResolveFailed(serviceInfo, FAILURE_INTERNAL_ERROR);
            }
        });
    }

    public void registerServiceInfoCallback(NsdServiceInfo serviceInfo, Executor executor, ServiceInfoCallback callback) {
        final String type = fqType(serviceInfo.getServiceType());
        final String name = serviceInfo.getServiceName();
        ServiceListener sl = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                if (name.equals(event.getName())) executor.execute(callback::onServiceLost);
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                if (name.equals(event.getName())) {
                    NsdServiceInfo nsd = toNsd(event.getInfo());
                    executor.execute(() -> callback.onServiceUpdated(nsd));
                }
            }
        };
        synchronized (mInfoCallbacks) {
            mInfoCallbacks.put(callback, new Object[]{type, sl});
        }
        EXECUTOR.execute(() -> {
            try {
                dns().addServiceListener(type, sl);
                for (ServiceInfo info : dns().getServiceInfos(type, name, 6000)) {
                    if (info != null && info.getInet4Addresses().length + info.getInet6Addresses().length > 0) {
                        NsdServiceInfo nsd = toNsd(info);
                        executor.execute(() -> callback.onServiceUpdated(nsd));
                        break;
                    }
                }
            } catch (Throwable t) {
                executor.execute(() -> callback.onServiceInfoCallbackRegistrationFailed(FAILURE_INTERNAL_ERROR));
            }
        });
    }

    public void unregisterServiceInfoCallback(ServiceInfoCallback callback) {
        Object[] d;
        synchronized (mInfoCallbacks) {
            d = mInfoCallbacks.remove(callback);
        }
        if (d == null) return;
        EXECUTOR.execute(() -> {
            dns().removeServiceListener((String) d[0], (ServiceListener) d[1]);
            callback.onServiceInfoCallbackUnregistered();
        });
    }

    public interface DiscoveryListener {
        void onStartDiscoveryFailed(String serviceType, int errorCode);

        void onStopDiscoveryFailed(String serviceType, int errorCode);

        void onDiscoveryStarted(String serviceType);

        void onDiscoveryStopped(String serviceType);

        void onServiceFound(NsdServiceInfo serviceInfo);

        void onServiceLost(NsdServiceInfo serviceInfo);
    }

    public interface RegistrationListener {
        void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onServiceRegistered(NsdServiceInfo serviceInfo);

        void onServiceUnregistered(NsdServiceInfo serviceInfo);
    }

    public interface ResolveListener {
        void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onServiceResolved(NsdServiceInfo serviceInfo);

        default void onResolutionStopped(NsdServiceInfo serviceInfo) {
        }

        default void onStopResolutionFailed(NsdServiceInfo serviceInfo, int errorCode) {
        }
    }

    public interface ServiceInfoCallback {
        void onServiceInfoCallbackRegistrationFailed(int errorCode);

        void onServiceUpdated(NsdServiceInfo serviceInfo);

        void onServiceLost();

        void onServiceInfoCallbackUnregistered();
    }
}
