package com.metehanyl.calarsaat.data

import java.security.MessageDigest

object PinHasher {
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
