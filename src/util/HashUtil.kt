package de.ka.rgreed.util

import io.ktor.util.hex
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Represents a basic hash util to provide hashing utility features.
 */
object HashUtil {

    /**
     * Hardcoded secret hash key used to hash string, passwords etc..
     */
    private val hashKey = hex("6819b57a326945c1968f45236589")

    /**
     * HMac SHA1 key spec for the password hashing.
     */
    private val hmacKey = SecretKeySpec(hashKey, "HmacSHA1")

    // Provides a hash function to be used when registering the resources.
    val hashString = { string: String -> hash(string) }

    /**
     * Method that hashes a [password] by using the globally defined secret key [hmacKey].
     */
    private fun hash(password: String): String {
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(hmacKey)
        return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
    }
}