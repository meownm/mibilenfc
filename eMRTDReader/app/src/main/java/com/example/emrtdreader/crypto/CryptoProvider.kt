package com.example.emrtdreader.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoProvider {

    fun ensureBC() {
        val existing = Security.getProvider("BC")
        if (existing == null || existing.javaClass.name != BouncyCastleProvider::class.java.name) {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    // алиас на случай если где-то уже вызвали ensureBouncyCastle()
    fun ensureBouncyCastle() = ensureBC()
}
