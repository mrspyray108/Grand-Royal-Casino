package com.example.data.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class GameCategory(val displayName: String) {
    ALL("All Games"),
    SLOTS("Slots"),
    TABLE("Table"),
    DICE_CRASH("Dice & Crash"),
    CARDS("Cards"),
    NUMBERS("Numbers")
}

enum class GameType(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: GameCategory,
    val targetRtp: Double,
    val volatility: String,
    val minBet: Long,
    val maxBet: Long
) {
    SLOTS(
        id = "slots",
        title = "Royal Gold Slots",
        subtitle = "5-Reel • 20 Lines • Free Spins",
        category = GameCategory.SLOTS,
        targetRtp = 96.5,
        volatility = "Medium-High",
        minBet = 10,
        maxBet = 5000
    ),
    BLACKJACK(
        id = "blackjack",
        title = "Vegas Blackjack 21",
        subtitle = "Double • Split • 3:2 Payout",
        category = GameCategory.CARDS,
        targetRtp = 99.4,
        volatility = "Low",
        minBet = 25,
        maxBet = 10000
    ),
    ROULETTE(
        id = "roulette",
        title = "European Roulette",
        subtitle = "Single Zero • Inside & Outside",
        category = GameCategory.TABLE,
        targetRtp = 97.3,
        volatility = "Medium",
        minBet = 10,
        maxBet = 5000
    ),
    BACCARAT(
        id = "baccarat",
        title = "Grand Baccarat",
        subtitle = "Player • Banker • Punto Banco",
        category = GameCategory.CARDS,
        targetRtp = 98.9,
        volatility = "Low",
        minBet = 25,
        maxBet = 10000
    ),
    CRASH(
        id = "crash",
        title = "Rocket Crash Aviator",
        subtitle = "Rising Multiplier • Cash Out",
        category = GameCategory.DICE_CRASH,
        targetRtp = 97.0,
        volatility = "Very High",
        minBet = 10,
        maxBet = 5000
    ),
    DICE(
        id = "dice",
        title = "Provable Slider Dice",
        subtitle = "Over / Under • Live Odds Slider",
        category = GameCategory.DICE_CRASH,
        targetRtp = 98.0,
        volatility = "Adjustable",
        minBet = 10,
        maxBet = 10000
    ),
    COIN_FLIP(
        id = "coin_flip",
        title = "Royal Coin Flip",
        subtitle = "Heads or Tails • Streak Multiplier",
        category = GameCategory.DICE_CRASH,
        targetRtp = 97.5,
        volatility = "Medium",
        minBet = 10,
        maxBet = 5000
    ),
    VIDEO_POKER(
        id = "video_poker",
        title = "Jacks or Better Poker",
        subtitle = "Hold & Draw • Royal Flush 800x",
        category = GameCategory.CARDS,
        targetRtp = 98.2,
        volatility = "Medium",
        minBet = 20,
        maxBet = 2500
    ),
    PLINKO(
        id = "plinko",
        title = "Pyramid Plinko",
        subtitle = "Peg Physics • Up to 1000x",
        category = GameCategory.TABLE,
        targetRtp = 97.0,
        volatility = "Selectable",
        minBet = 10,
        maxBet = 5000
    ),
    KENO(
        id = "keno",
        title = "Lucky 80 Keno",
        subtitle = "Pick 1-10 • Draw 20 Balls",
        category = GameCategory.NUMBERS,
        targetRtp = 96.0,
        volatility = "High",
        minBet = 10,
        maxBet = 2000
    )
}

enum class LedgerType {
    BET,
    PAYOUT,
    DAILY_BONUS,
    WELCOME_BONUS,
    STORE_PACK,
    REFUND
}

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LedgerType,
    val gameType: GameType? = null,
    val amount: Long, // Positive for credits, negative for debits
    val balanceAfter: Long,
    val idempotencyKey: String,
    val note: String = ""
)

enum class VipTier(val title: String, val threshold: Long, val badgeColor: Long) {
    BRONZE("Bronze", 0L, 0xFFCD7F32),
    SILVER("Silver", 50_000L, 0xFFC0C0C0),
    GOLD("Gold", 200_000L, 0xFFFFD700),
    PLATINUM("Platinum", 1_000_000L, 0xFFE5E4E2),
    DIAMOND("Diamond", 5_000_000L, 0xFFB9F2FF)
}

data class ProvablyFairRound(
    val roundId: String,
    val gameType: GameType,
    val serverSeedHashed: String,
    val serverSeedSecret: String, // Revealed after round or available for audit
    val clientSeed: String,
    val nonce: Long,
    val outcomeHash: String,
    val outcomeSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ResponsibleGamingSettings(
    val isAgeGateAccepted: Boolean = false,
    val dailyLossLimit: Long = 0L, // 0 means no limit set
    val currentDailyLoss: Long = 0L,
    val sessionStartTimeMillis: Long = System.currentTimeMillis(),
    val selfExclusionUntilMillis: Long = 0L, // 0 = not excluded
    val sessionReminderMinutes: Int = 30
)

data class ChipStorePack(
    val id: String,
    val chips: Long,
    val bonusChips: Long,
    val label: String,
    val badge: String? = null,
    val priceLabel: String // clearly labeled play-money simulator price
)
