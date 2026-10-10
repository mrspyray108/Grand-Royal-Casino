package com.example.data.repository

import com.example.data.config.CasinoConfig
import com.example.data.model.ChipStorePack
import com.example.data.model.GameType
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.ProvablyFairRound
import com.example.data.model.ResponsibleGamingSettings
import com.example.data.model.UserProfile
import com.example.data.model.VipTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReconciliationReport(
    val currentBalance: Long,
    val sumOfLedgerEntries: Long,
    val totalTransactions: Int,
    val isReconciled: Boolean,
    val details: String
)

object CasinoRepository {
    private val processedIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()

    private val _balance = MutableStateFlow(CasinoConfig.WELCOME_BONUS_AMOUNT)
    val balance: StateFlow<Long> = _balance.asStateFlow()

    private val _ledgerEntries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledgerEntries: StateFlow<List<LedgerEntry>> = _ledgerEntries.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _fairnessHistory = MutableStateFlow<List<ProvablyFairRound>>(emptyList())
    val fairnessHistory: StateFlow<List<ProvablyFairRound>> = _fairnessHistory.asStateFlow()

    private val _responsibleSettings = MutableStateFlow(ResponsibleGamingSettings())
    val responsibleSettings: StateFlow<ResponsibleGamingSettings> = _responsibleSettings.asStateFlow()

    private val _favoriteGameIds = MutableStateFlow<Set<String>>(setOf("slots", "blackjack", "crash"))
    val favoriteGameIds: StateFlow<Set<String>> = _favoriteGameIds.asStateFlow()

    private val _recentGameIds = MutableStateFlow<List<String>>(listOf("slots", "roulette"))
    val recentGameIds: StateFlow<List<String>> = _recentGameIds.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(true)
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(true)
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private var lastDailyBonusClaimTimestamp: Long = 0L
    private var roundNonceCounter: Long = 1L

    init {
        // Record Welcome Bonus Initial Ledger Entry
        val welcomeKey = "welcome_bonus_initial"
        processedIdempotencyKeys.add(welcomeKey)
        val initialEntry = LedgerEntry(
            id = "tx_welcome",
            type = LedgerType.WELCOME_BONUS,
            amount = CasinoConfig.WELCOME_BONUS_AMOUNT,
            balanceAfter = CasinoConfig.WELCOME_BONUS_AMOUNT,
            idempotencyKey = welcomeKey,
            note = "Welcome Bonus: 10,000 Play Chips Credited"
        )
        _ledgerEntries.value = listOf(initialEntry)
    }

    fun getNextNonce(): Long = synchronized(this) {
        roundNonceCounter++
    }

    /**
     * Deducts bet amount atomically and records ledger entry.
     * Prevents duplicate execution via idempotency key and guarantees balance cannot be negative.
     */
    @Synchronized
    fun placeBet(
        gameType: GameType,
        amount: Long,
        idempotencyKey: String,
        note: String = ""
    ): Result<LedgerEntry> {
        // Check self-exclusion
        val settings = _responsibleSettings.value
        if (settings.selfExclusionUntilMillis > System.currentTimeMillis()) {
            val remainingHours = (settings.selfExclusionUntilMillis - System.currentTimeMillis()) / (1000 * 60 * 60) + 1
            return Result.failure(IllegalStateException("Self-exclusion active. You can play again in $remainingHours hour(s)."))
        }

        // Check loss limit
        if (settings.dailyLossLimit > 0 && (settings.currentDailyLoss + amount) > settings.dailyLossLimit) {
            return Result.failure(IllegalStateException("Daily loss limit reached ($${settings.dailyLossLimit} chips). Take a break!"))
        }

        // Idempotency check
        if (processedIdempotencyKeys.contains(idempotencyKey)) {
            val existing = _ledgerEntries.value.find { it.idempotencyKey == idempotencyKey }
            if (existing != null) return Result.success(existing)
            return Result.failure(IllegalStateException("Duplicate transaction key rejected: $idempotencyKey"))
        }

        if (amount <= 0) {
            return Result.failure(IllegalArgumentException("Bet amount must be greater than zero."))
        }

        val currentBal = _balance.value
        if (currentBal < amount) {
            return Result.failure(IllegalStateException("Insufficient chips. Current balance: $currentBal chips, Required: $amount chips."))
        }

        processedIdempotencyKeys.add(idempotencyKey)
        val newBal = currentBal - amount
        _balance.value = newBal

        val entry = LedgerEntry(
            type = LedgerType.BET,
            gameType = gameType,
            amount = -amount,
            balanceAfter = newBal,
            idempotencyKey = idempotencyKey,
            note = note.ifEmpty { "Bet placed on ${gameType.title}" }
        )
        _ledgerEntries.value = listOf(entry) + _ledgerEntries.value

        // Update stats and VIP progress
        val profile = _userProfile.value
        val updatedBets = profile.totalBets + amount
        val newTier = calculateVipTier(updatedBets)
        _userProfile.value = profile.copy(
            totalBets = updatedBets,
            roundsPlayed = profile.roundsPlayed + 1,
            vipTier = newTier
        )

        // Track daily loss
        _responsibleSettings.value = settings.copy(
            currentDailyLoss = settings.currentDailyLoss + amount
        )

        recordGamePlayed(gameType.id)
        return Result.success(entry)
    }

    /**
     * Credits payout atomically and records ledger entry.
     */
    @Synchronized
    fun creditPayout(
        gameType: GameType,
        amount: Long,
        idempotencyKey: String,
        note: String = ""
    ): LedgerEntry {
        if (processedIdempotencyKeys.contains(idempotencyKey)) {
            val existing = _ledgerEntries.value.find { it.idempotencyKey == idempotencyKey }
            if (existing != null) return existing
        }
        processedIdempotencyKeys.add(idempotencyKey)

        val newBal = _balance.value + amount
        _balance.value = newBal

        val entry = LedgerEntry(
            type = LedgerType.PAYOUT,
            gameType = gameType,
            amount = amount,
            balanceAfter = newBal,
            idempotencyKey = idempotencyKey,
            note = note.ifEmpty { "Payout won on ${gameType.title}" }
        )
        _ledgerEntries.value = listOf(entry) + _ledgerEntries.value

        val profile = _userProfile.value
        _userProfile.value = profile.copy(
            totalWon = profile.totalWon + amount
        )

        // If win offsets daily loss
        val settings = _responsibleSettings.value
        val adjustedLoss = (settings.currentDailyLoss - amount).coerceAtLeast(0L)
        _responsibleSettings.value = settings.copy(currentDailyLoss = adjustedLoss)

        return entry
    }

    fun recordProvablyFairRound(round: ProvablyFairRound) {
        _fairnessHistory.value = (listOf(round) + _fairnessHistory.value).take(50)
    }

    @Synchronized
    fun claimDailyBonus(): Result<Long> {
        val now = System.currentTimeMillis()
        if (now - lastDailyBonusClaimTimestamp < CasinoConfig.DAILY_BONUS_COOLDOWN_MS) {
            val remainingMs = CasinoConfig.DAILY_BONUS_COOLDOWN_MS - (now - lastDailyBonusClaimTimestamp)
            val hours = remainingMs / (1000 * 60 * 60)
            val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
            return Result.failure(IllegalStateException("Daily bonus available in ${hours}h ${minutes}m"))
        }

        lastDailyBonusClaimTimestamp = now
        val tier = _userProfile.value.vipTier
        val multiplier = when (tier) {
            VipTier.BRONZE -> 1.0
            VipTier.SILVER -> 1.2
            VipTier.GOLD -> 1.5
            VipTier.PLATINUM -> 2.0
            VipTier.DIAMOND -> 3.0
        }
        val bonusAmount = (CasinoConfig.BASE_DAILY_BONUS * multiplier).toLong()
        val idempotencyKey = "daily_bonus_${System.currentTimeMillis() / (24 * 60 * 60 * 1000L)}"
        processedIdempotencyKeys.add(idempotencyKey)

        val newBal = _balance.value + bonusAmount
        _balance.value = newBal

        val entry = LedgerEntry(
            type = LedgerType.DAILY_BONUS,
            amount = bonusAmount,
            balanceAfter = newBal,
            idempotencyKey = idempotencyKey,
            note = "Daily Free Chips Bonus (${tier.title} Tier: +${((multiplier - 1.0) * 100).toInt()}%)"
        )
        _ledgerEntries.value = listOf(entry) + _ledgerEntries.value
        return Result.success(bonusAmount)
    }

    fun timeUntilDailyBonus(): Long {
        val elapsed = System.currentTimeMillis() - lastDailyBonusClaimTimestamp
        return (CasinoConfig.DAILY_BONUS_COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    @Synchronized
    fun purchaseStorePack(pack: ChipStorePack): LedgerEntry {
        val totalCredited = pack.chips + pack.bonusChips
        val idempotencyKey = "pack_${pack.id}_${UUID.randomUUID()}"
        processedIdempotencyKeys.add(idempotencyKey)

        val newBal = _balance.value + totalCredited
        _balance.value = newBal

        val entry = LedgerEntry(
            type = LedgerType.STORE_PACK,
            amount = totalCredited,
            balanceAfter = newBal,
            idempotencyKey = idempotencyKey,
            note = "${pack.label} Claimed (${pack.chips} + ${pack.bonusChips} bonus)"
        )
        _ledgerEntries.value = listOf(entry) + _ledgerEntries.value
        return entry
    }

    /**
     * Verifies that current balance strictly matches the cumulative sum of all ledger entries
     */
    fun reconcileBalance(): ReconciliationReport {
        val entries = _ledgerEntries.value
        val sum = entries.sumOf { it.amount }
        val current = _balance.value
        val isReconciled = sum == current

        val details = if (isReconciled) {
            "Ledger is 100% balanced across ${entries.size} verified transactions. Total Credits - Debits = $sum chips."
        } else {
            "Discrepancy detected: Ledger sum ($sum) != Current balance ($current). Discrepancy: ${current - sum} chips."
        }

        return ReconciliationReport(
            currentBalance = current,
            sumOfLedgerEntries = sum,
            totalTransactions = entries.size,
            isReconciled = isReconciled,
            details = details
        )
    }

    fun toggleFavorite(gameId: String) {
        val current = _favoriteGameIds.value.toMutableSet()
        if (current.contains(gameId)) current.remove(gameId) else current.add(gameId)
        _favoriteGameIds.value = current
    }

    fun recordGamePlayed(gameId: String) {
        val current = _recentGameIds.value.filter { it != gameId }.toMutableList()
        current.add(0, gameId)
        _recentGameIds.value = current.take(6)
    }

    fun updateClientSeed(seed: String) {
        _userProfile.value = _userProfile.value.copy(clientSeed = seed.trim().ifEmpty { "royal-seed" })
    }

    fun updateProfile(username: String, emoji: String) {
        _userProfile.value = _userProfile.value.copy(
            username = username.ifBlank { "HighRoller77" },
            avatarEmoji = emoji
        )
    }

    fun setSelfExclusion(durationHours: Int) {
        val until = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)
        _responsibleSettings.value = _responsibleSettings.value.copy(selfExclusionUntilMillis = until)
    }

    fun cancelSelfExclusion() {
        _responsibleSettings.value = _responsibleSettings.value.copy(selfExclusionUntilMillis = 0L)
    }

    fun setDailyLossLimit(limit: Long) {
        _responsibleSettings.value = _responsibleSettings.value.copy(dailyLossLimit = limit)
    }

    fun acceptAgeGate() {
        _responsibleSettings.value = _responsibleSettings.value.copy(isAgeGateAccepted = true)
    }

    fun toggleSound() {
        _isSoundEnabled.value = !_isSoundEnabled.value
    }

    fun toggleHaptics() {
        _isHapticsEnabled.value = !_isHapticsEnabled.value
    }

    private fun calculateVipTier(totalBets: Long): VipTier = when {
        totalBets >= VipTier.DIAMOND.threshold -> VipTier.DIAMOND
        totalBets >= VipTier.PLATINUM.threshold -> VipTier.PLATINUM
        totalBets >= VipTier.GOLD.threshold -> VipTier.GOLD
        totalBets >= VipTier.SILVER.threshold -> VipTier.SILVER
        else -> VipTier.BRONZE
    }
}
