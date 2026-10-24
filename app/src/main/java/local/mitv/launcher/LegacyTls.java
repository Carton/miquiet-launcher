package local.mitv.launcher;

import android.content.res.Resources;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Add two public roots missing from this Android 6 device; keep chain and hostname validation. */
final class LegacyTls {
    private static SSLSocketFactory sockets;
    private LegacyTls() {}

    static void configure(HttpsURLConnection connection, Resources resources) throws Exception {
        String host = connection.getURL().getHost();
        // Extra trust applies only to these data service hosts, not arbitrary article links.
        if (!(host.equals("ipwho.is") || host.equals("api.open-meteo.com")
            || host.equals("geocoding-api.open-meteo.com") || host.equals("api-one.wallstcn.com")
            || host.equals("www.ithome.com") || host.equals("www.gcores.com") || host.equals("www.qbitai.com"))) return;
        synchronized (LegacyTls.class) {
            if (sockets == null) {
                try (InputStream isrg = resources.openRawResource(R.raw.isrg_root_x1);
                     InputStream google = resources.openRawResource(R.raw.gts_root_r4)) {
                    sockets = factory(isrg, google);
                }
            }
            connection.setSSLSocketFactory(sockets);
            // HttpsURLConnection's default hostname verifier is deliberately unchanged.
        }
    }

    static SSLSocketFactory factory(InputStream... roots) throws Exception {
        TrustManagerFactory nativeFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        nativeFactory.init((KeyStore)null);
        X509TrustManager platform = manager(nativeFactory);
        KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
        extraStore.load(null);
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < roots.length; i++) {
            X509Certificate certificate = (X509Certificate)certificates.generateCertificate(roots[i]);
            certificate.checkValidity();
            if (certificate.getBasicConstraints() < 0) throw new CertificateException("Expected a CA certificate");
            extraStore.setCertificateEntry("public-root-" + i, certificate);
        }
        TrustManagerFactory extraFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        extraFactory.init(extraStore);
        X509TrustManager extra = manager(extraFactory);
        X509TrustManager trust = new X509TrustManager() {
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try { platform.checkServerTrusted(chain, authType); }
                catch (CertificateException original) {
                    try { extra.checkServerTrusted(chain, authType); }
                    catch (CertificateException rejected) { rejected.addSuppressed(original); throw rejected; }
                }
            }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                platform.checkClientTrusted(chain, authType);
            }
            @Override public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] nativeRoots = platform.getAcceptedIssuers(), extraRoots = extra.getAcceptedIssuers();
                X509Certificate[] all = new X509Certificate[nativeRoots.length + extraRoots.length];
                System.arraycopy(nativeRoots, 0, all, 0, nativeRoots.length);
                System.arraycopy(extraRoots, 0, all, nativeRoots.length, extraRoots.length);
                return all;
            }
        };
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new TrustManager[]{trust}, null);
        return tls.getSocketFactory();
    }

    private static X509TrustManager manager(TrustManagerFactory factory) {
        for (TrustManager trust : factory.getTrustManagers()) if (trust instanceof X509TrustManager) return (X509TrustManager)trust;
        throw new IllegalStateException("No X509 trust manager");
    }
}
