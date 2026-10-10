package com.example

import com.example.data.config.CasinoConfig
import com.example.data.model.GameType
import com.example.data.repository.CasinoRepository
import com.example.data.repository.ProvablyFairEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CasinoUnitTest {

    @Test
    fun testProvablyFairEngineSha256AndHmac() {
        val serverSeed = "test_server_secret_seed_12345678"
        val serverSeedHash = ProvablyFairEngine.sha256(serverSeed)
        assertNotNull(serverSeedHash)
        assertEquals(64, serverSeedHash.length)

        val clientSeed = "client_seed_abc"
        val nonce = 42L
        val outcome = ProvablyFairEngine.hmacSha256(serverSeed, "$clientSeed:$nonce")
        assertEquals(64, outcome.length)

        // Verify audit verification function
        val audit = ProvablyFairEngine.verifyAudit(
            serverSeedSecret = serverSeed,
            serverSeedHashExpected = serverSeedHash,
            clientSeed = clientSeed,
            nonce = nonce,
            expectedOutcomeHash = outcome
        )
        assertTrue(audit.isServerSeedAuthentic)
        assertTrue(audit.isOutcomeAuthentic)
        assertTrue(audit.is100PercentFair)
        assertTrue(audit.reconstructedOutcomeFloat in 0.0..1.0)
    }

    @Test
    fun testWalletIdempotencyAndNoNegativeBalance() {
        val startingBalance = CasinoRepository.balance.value
        val uniqueKey = "tx_test_${UUID.randomUUID()}"

        // Place a bet
        val betAmount = 100L
        val res1 = CasinoRepository.placeBet(
            gameType = GameType.DICE,
            amount = betAmount,
            idempotencyKey = uniqueKey,
            note = "Test bet"
        )
        assertTrue(res1.isSuccess)
        assertEquals(startingBalance - betAmount, CasinoRepository.balance.value)

        // Duplicate bet with identical idempotency key must not charge again
        val resDuplicate = CasinoRepository.placeBet(
            gameType = GameType.DICE,
            amount = betAmount,
            idempotencyKey = uniqueKey,
            note = "Duplicate attempt"
        )
        // Should succeed returning existing entry without deducting extra
        assertEquals(startingBalance - betAmount, CasinoRepository.balance.value)

        // Bet exceeding balance must be rejected
        val excessiveBet = CasinoRepository.balance.value + 50_000L
        val resExceed = CasinoRepository.placeBet(
            gameType = GameType.BLACKJACK,
            amount = excessiveBet,
            idempotencyKey = "tx_exceed_${UUID.randomUUID()}",
            note = "Exceeding bet"
        )
        assertTrue(resExceed.isFailure)
    }

    @Test
    fun testLedgerReconciliation() {
        // Run balance reconciliation
        val report = CasinoRepository.reconcileBalance()
        assertTrue("Ledger sum must strictly match wallet balance", report.isReconciled)
        assertEquals(report.currentBalance, report.sumOfLedgerEntries)
    }

    @Test
    fun testRtpConfigurations() {
        for (game in GameType.values()) {
            val rtp = CasinoConfig.RTP_CONFIG[game]
            assertNotNull("RTP must be configured for $game", rtp)
            assertTrue("RTP must be within 95.0% - 99.5%", rtp!! in 95.0..99.5)
        }
    }
}
