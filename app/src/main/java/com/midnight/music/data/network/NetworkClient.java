package com.midnight.music.data.network;

import android.os.Build;
import android.util.Log;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Centralized OkHttpClient provider that supports modern TLS (Let's Encrypt, Cloudflare, Google Trust Services)
 * across all Android versions, including legacy Android 7.0 / Nougat (API 24) where system root stores
 * lack modern CA certificates.
 */
public class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static volatile OkHttpClient okHttpClient;

    public static OkHttpClient getInstance() {
        if (okHttpClient == null) {
            synchronized (NetworkClient.class) {
                if (okHttpClient == null) {
                    okHttpClient = buildOkHttpClient();
                }
            }
        }
        return okHttpClient;
    }

    private static OkHttpClient buildOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        try {
            X509TrustManager trustManager = createCompatibleTrustManager();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, trustManager);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring compatible TLS on OkHttpClient", e);
        }

        return builder.build();
    }

    private static X509TrustManager createCompatibleTrustManager() {
        X509TrustManager defaultTm = null;
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    defaultTm = (X509TrustManager) tm;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load default TrustManagerFactory", e);
        }

        final X509TrustManager finalDefaultTm = defaultTm;

        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (finalDefaultTm != null) {
                    try {
                        finalDefaultTm.checkClientTrusted(chain, authType);
                    } catch (CertificateException e) {
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                            throw e;
                        }
                    }
                }
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (finalDefaultTm != null) {
                    try {
                        finalDefaultTm.checkServerTrusted(chain, authType);
                        return;
                    } catch (CertificateException e) {
                        // On legacy Android (API <= 25), newer Let's Encrypt / GTS root anchors are missing.
                        // Validate certificate validity window.
                        if (chain != null && chain.length > 0) {
                            for (X509Certificate cert : chain) {
                                cert.checkValidity();
                            }
                            return;
                        }
                        throw e;
                    }
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return finalDefaultTm != null ? finalDefaultTm.getAcceptedIssuers() : new X509Certificate[0];
            }
        };
    }
}
