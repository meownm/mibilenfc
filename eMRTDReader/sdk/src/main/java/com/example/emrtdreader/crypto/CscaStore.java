package com.example.emrtdreader.crypto;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Loads CSCA certificates from assets/csca/*.cer (or .crt/.der).
 */
public final class CscaStore {
    private CscaStore() {}

    public static List<X509Certificate> loadFromAssets(Context ctx) {
        List<X509Certificate> out = new ArrayList<>();
        try {
            AssetManager am = ctx.getAssets();
            String[] files = am.list("csca");
            if (files == null) return out;

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (String name : files) {
                if (!name.endsWith(".cer") && !name.endsWith(".crt") && !name.endsWith(".der")) continue;
                try (InputStream in = am.open("csca/" + name)) {
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
                    out.add(cert);
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}
        return out;
    }
}
