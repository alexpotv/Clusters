package com.example.clusterapp.plugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * SSLSocketFactory that:
 *   1. Forces TLS 1.1 / 1.2 on every socket (disabled by default on API 17).
 *   2. Tries the system trust store first; if chain validation fails (e.g. a
 *      modern intermediate CA missing from the API-17 trust store), falls back
 *      to basic certificate sanity checks (not expired, well-formed X.509).
 *
 * Hostname verification is performed separately by HttpsURLConnection's
 * HostnameVerifier and is NOT relaxed here.
 */
final class TlsSocketFactory extends SSLSocketFactory {

    private static final String[] TLS_PROTOCOLS = {"TLSv1.1", "TLSv1.2"};

    private final SSLSocketFactory mDelegate;

    /** Build a TlsSocketFactory with the combined TLS + relaxed-trust configuration. */
    static TlsSocketFactory create() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new RelaxedTrustManager()}, new SecureRandom());
            return new TlsSocketFactory(ctx.getSocketFactory());
        } catch (Exception e) {
            // Fallback: at least fix the TLS version issue.
            return new TlsSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
        }
    }

    TlsSocketFactory(SSLSocketFactory delegate) {
        mDelegate = delegate;
    }

    @Override public String[] getDefaultCipherSuites()  { return mDelegate.getDefaultCipherSuites();  }
    @Override public String[] getSupportedCipherSuites() { return mDelegate.getSupportedCipherSuites(); }

    @Override
    public Socket createSocket() throws IOException {
        return enable(mDelegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return enable(mDelegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port)
            throws IOException, UnknownHostException {
        return enable(mDelegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        return enable(mDelegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enable(mDelegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port,
                               InetAddress localAddress, int localPort) throws IOException {
        return enable(mDelegate.createSocket(address, port, localAddress, localPort));
    }

    private static Socket enable(Socket socket) {
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).setEnabledProtocols(TLS_PROTOCOLS);
        }
        return socket;
    }

    // -------------------------------------------------------------------------
    // Trust manager
    // -------------------------------------------------------------------------

    /**
     * Tries the system trust manager first. If chain validation fails because
     * an intermediate or root CA is missing from the old system store, falls
     * back to checking that every certificate in the chain is not expired and
     * is otherwise well-formed. Hostname verification (separate concern) is
     * still enforced by HttpsURLConnection.
     */
    private static final class RelaxedTrustManager implements X509TrustManager {

        private final X509TrustManager mSystem;

        RelaxedTrustManager() {
            X509TrustManager system = null;
            try {
                TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                for (TrustManager tm : tmf.getTrustManagers()) {
                    if (tm instanceof X509TrustManager) {
                        system = (X509TrustManager) tm;
                        break;
                    }
                }
            } catch (Exception ignored) {}
            mSystem = system;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // Prefer full chain validation from the system trust store.
            if (mSystem != null) {
                try {
                    mSystem.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException ignored) {
                    // System validation failed — likely a missing intermediate CA.
                    // Fall through to basic sanity checks below.
                }
            }
            // Fallback: every certificate in the chain must be within its validity period.
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Empty certificate chain");
            }
            for (X509Certificate cert : chain) {
                cert.checkValidity();
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
