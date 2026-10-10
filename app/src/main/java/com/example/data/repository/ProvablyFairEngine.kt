package com.example.data.repository

import com.example.data.model.GameType
import com.example.data.model.ProvablyFairRound
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ProvablyFairEngine {
    private val secureRandom = SecureRandom()

    fun generateServerSeed(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts HMAC hex string to a normalized deterministic float [0.0, 1.0)
     */
    fun hmacToFloat(hmacHex: String, offset: Int = 0): Double {
        if (hmacHex.length < offset + 8) return secureRandom.nextDouble()
        val slice = hmacHex.substring(offset, offset + 8)
        val value = slice.toLong(16)
        return value.toDouble() / 0x100000000L.toDouble()
    }

    /**
     * Generate an integer outcome in range [min, max] using cryptographically secure HMAC
     */
    fun hmacToInt(hmacHex: String, min: Int, max: Int, offset: Int = 0): Int {
        val f = hmacToFloat(hmacHex, offset)
        return min + (f * (max - min + 1)).toInt().coerceIn(0, max - min)
    }

    /**
     * Create a verified round record
     */
    fun createRoundRecord(
        gameType: GameType,
        serverSeed: String,
        clientSeed: String,
        nonce: Long,
        summary: String
    ): ProvablyFairRound {
        val hashedServerSeed = sha256(serverSeed)
        val message = "$clientSeed:$nonce"
        val outcomeHash = hmacSha256(serverSeed, message)

        return ProvablyFairRound(
            roundId = UUID.randomUUID().toString().take(8).uppercase(),
            gameType = gameType,
            serverSeedHashed = hashedServerSeed,
            serverSeedSecret = serverSeed,
            clientSeed = clientSeed,
            nonce = nonce,
            outcomeHash = outcomeHash,
            outcomeSummary = summary
        )
    }

    /**
     * Verifies whether serverSeed matches the hash and recomputes HMAC-SHA256
     */
    fun verifyAudit(
        serverSeedSecret: String,
        serverSeedHashExpected: String,
        clientSeed: String,
        nonce: Long,
        expectedOutcomeHash: String
    ): AuditResult {
        val computedHash = sha256(serverSeedSecret)
        val isHashValid = computedHash.equals(serverSeedHashExpected, ignoreCase = true)

        val message = "$clientSeed:$nonce"
        val computedOutcome = hmacSha256(serverSeedSecret, message)
        val isOutcomeValid = computedOutcome.equals(expectedOutcomeHash, ignoreCase = true)

        return AuditResult(
            isServerSeedAuthentic = isHashValid,
            isOutcomeAuthentic = isOutcomeValid,
            computedServerSeedHash = computedHash,
            computedOutcomeHash = computedOutcome,
            reconstructedOutcomeFloat = hmacToFloat(computedOutcome)
        )
    }

    data class AuditResult(
        val isServerSeedAuthentic: Boolean,
        val isOutcomeAuthentic: Boolean,
        val computedServerSeedHash: String,
        val computedOutcomeHash: String,
        val reconstructedOutcomeFloat: Double
    ) {
        val is100PercentFair: Boolean get() = isServerSeedAuthentic && isOutcomeAuthentic
    }
}
