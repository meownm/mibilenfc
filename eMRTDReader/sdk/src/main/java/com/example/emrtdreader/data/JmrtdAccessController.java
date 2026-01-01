package com.example.emrtdreader.data;

import com.example.emrtdreader.domain.AccessKey;
import com.example.emrtdreader.error.PassportReadException;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Access controller:
 * - Tries PACE (CAN if provided, otherwise MRZ key if supported)
 * - Falls back to BAC for maximal compatibility
 */
public class JmrtdAccessController {
    private final PassportService service;

    public JmrtdAccessController(PassportService service) {
        this.service = service;
    }

    public void establish(AccessKey.Mrz mrz, String can) throws PassportReadException.AccessFailed {
        // 1) Try PACE (best-effort)
        try {
            if (tryPace(mrz, can)) return;
        } catch (Throwable ignore) {
            // fall back
        }

        // 2) BAC
        try {
            BACKey bacKey = new BACKey(mrz.documentNumber, mrz.dateOfBirthYYMMDD, mrz.dateOfExpiryYYMMDD);
            service.doBAC(bacKey);
        } catch (Throwable e) {
            throw new PassportReadException.AccessFailed("BAC failed", e);
        }
    }

    private boolean tryPace(AccessKey.Mrz mrz, String can) throws Exception {
        // Read EF.CardAccess to detect PACE support
        InputStream in;
        try {
            in = service.getInputStream(PassportService.EF_CARD_ACCESS);
        } catch (Throwable t) {
            return false;
        }

        byte[] cardAccessBytes;
        try (InputStream s = in) {
            cardAccessBytes = s.readAllBytes();
        }

        // Parse CardAccessFile via reflection to avoid strict dependency on package names
        Object cardAccessFile;
        try {
            Class<?> cafCls = Class.forName("org.jmrtd.lds.CardAccessFile");
            Constructor<?> ctor = cafCls.getConstructor(InputStream.class);
            cardAccessFile = ctor.newInstance(new java.io.ByteArrayInputStream(cardAccessBytes));
        } catch (Throwable t) {
            return false;
        }

        // Get PACEInfos list
        List<?> paceInfos;
        try {
            Method m = cardAccessFile.getClass().getMethod("getPACEInfos");
            Object res = m.invoke(cardAccessFile);
            if (!(res instanceof List)) return false;
            paceInfos = (List<?>) res;
            if (paceInfos.isEmpty()) return false;
        } catch (Throwable t) {
            return false;
        }

        // Create PACEKeySpec (CAN preferred; MRZ as fallback)
        Object paceKeySpec = createPaceKeySpec(mrz, can);
        if (paceKeySpec == null) return false;

        // Use first PACEInfo as parameters (OID, parameterId, etc.)
        Object paceInfo = paceInfos.get(0);

        // Try calling PassportService.doPACE(...)
        // Known overloads differ across jmrtd versions: doPACE(PACEKeySpec, PACEInfo) or doPACE(PACEKeySpec, String oid, int parameterId, ...)
        try {
            Method doPace = service.getClass().getMethod("doPACE", paceKeySpec.getClass(), paceInfo.getClass());
            doPace.invoke(service, paceKeySpec, paceInfo);
            return true;
        } catch (NoSuchMethodException nsme) {
            // try: doPACE(PACEKeySpec, String, int)
            try {
                Method getOid = paceInfo.getClass().getMethod("getObjectIdentifier");
                Object oidObj = getOid.invoke(paceInfo);
                String oid = String.valueOf(oidObj);

                Method getParamId = paceInfo.getClass().getMethod("getParameterId");
                int paramId = (Integer) getParamId.invoke(paceInfo);

                Method doPace2 = service.getClass().getMethod("doPACE", paceKeySpec.getClass(), String.class, int.class);
                doPace2.invoke(service, paceKeySpec, oid, paramId);
                return true;
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private Object createPaceKeySpec(AccessKey.Mrz mrz, String can) {
        try {
            Class<?> cls = Class.forName("org.jmrtd.PACEKeySpec");

            if (can != null && !can.trim().isEmpty()) {
                // constructor PACEKeySpec(String can)
                try {
                    Constructor<?> c = cls.getConstructor(String.class);
                    return c.newInstance(can.trim());
                } catch (Throwable ignore) {}
            }

            // fallback: PACEKeySpec(String docNumber, String dob, String doe)
            try {
                Constructor<?> c = cls.getConstructor(String.class, String.class, String.class);
                return c.newInstance(mrz.documentNumber, mrz.dateOfBirthYYMMDD, mrz.dateOfExpiryYYMMDD);
            } catch (Throwable ignore) {}

            // fallback: PACEKeySpec(BACKey)
            try {
                Constructor<?> c = cls.getConstructor(BACKey.class);
                BACKey bacKey = new BACKey(mrz.documentNumber, mrz.dateOfBirthYYMMDD, mrz.dateOfExpiryYYMMDD);
                return c.newInstance(bacKey);
            } catch (Throwable ignore) {}

            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
