package swimminsparrow.ricordamelo.core.utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Custom SSLSocketFactory that forces IPv4 resolution for hostnames.
 * Prevents IPv6 subnet rate-limiting (HTTP 429) on public ntfy instances.
 */
public class ForceIPv4SSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public ForceIPv4SSLSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate != null ? delegate : (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public static void installAsDefault() {
        try {
            SSLSocketFactory current = HttpsURLConnection.getDefaultSSLSocketFactory();
            if (!(current instanceof ForceIPv4SSLSocketFactory)) {
                HttpsURLConnection.setDefaultSSLSocketFactory(new ForceIPv4SSLSocketFactory(current));
            }
        } catch (Exception ignored) {}
    }

    public static void applyTo(java.net.URLConnection conn) {
        if (conn instanceof HttpsURLConnection) {
            try {
                SSLSocketFactory current = HttpsURLConnection.getDefaultSSLSocketFactory();
                if (current instanceof ForceIPv4SSLSocketFactory) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(current);
                } else {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(new ForceIPv4SSLSocketFactory(current));
                }
            } catch (Exception ignored) {}
        }
    }

    private InetAddress resolveIPv4(String host) throws UnknownHostException {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr instanceof Inet4Address) {
                    return addr;
                }
            }
        } catch (Exception ignored) {}

        if (host != null && host.contains("ntfy.sh")) {
            return InetAddress.getByName("159.203.148.75");
        }
        throw new UnknownHostException("No IPv4 address found for " + host);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        if (s != null && s.getInetAddress() instanceof java.net.Inet6Address) {
            try {
                InetAddress ipv4 = resolveIPv4(host);
                if (ipv4 instanceof Inet4Address) {
                    android.util.Log.d("ForceIPv4", "Redirecting socket from IPv6 (" + s.getInetAddress() + ") to IPv4 (" + ipv4 + ") for host: " + host);
                    try {
                        s.close();
                    } catch (Exception ignored) {}
                    Socket newRawSocket = new Socket();
                    newRawSocket.connect(new InetSocketAddress(ipv4, port), 10000);
                    return delegate.createSocket(newRawSocket, host, port, autoClose);
                }
            } catch (Exception e) {
                android.util.Log.w("ForceIPv4", "Failed redirecting to IPv4 for " + host + ": " + e.getMessage());
            }
        }
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        InetAddress ipv4 = resolveIPv4(host);
        Socket rawSocket = new Socket();
        rawSocket.connect(new InetSocketAddress(ipv4, port), 10000);
        return delegate.createSocket(rawSocket, host, port, true);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        InetAddress ipv4 = resolveIPv4(host);
        Socket rawSocket = new Socket();
        rawSocket.bind(new InetSocketAddress(localHost, localPort));
        rawSocket.connect(new InetSocketAddress(ipv4, port), 10000);
        return delegate.createSocket(rawSocket, host, port, true);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
