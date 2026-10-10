package com.example.ui.navigation

import com.example.data.model.GameType

sealed class Screen {
    object Lobby : Screen()
    object Wallet : Screen()
    object Fairness : Screen()
    object Leaderboard : Screen()
    object Profile : Screen()
    object ResponsibleGaming : Screen()
    data class Game(val gameType: GameType) : Screen()
}

enum class BottomNavTab(val label: String, val iconEmoji: String) {
    LOBBY("Lobby", "🎰"),
    WALLET("Wallet", "🪙"),
    FAIRNESS("Fairness", "🛡️"),
    LEADERBOARD("Ranks", "🏆"),
    PROFILE("Profile", "👑")
}
