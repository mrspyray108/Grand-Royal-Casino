package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.model.GameType
import com.example.data.repository.CasinoRepository
import com.example.ui.fairness.FairnessVerifyScreen
import com.example.ui.games.baccarat.BaccaratScreen
import com.example.ui.games.blackjack.BlackjackScreen
import com.example.ui.games.coinflip.CoinFlipScreen
import com.example.ui.games.crash.CrashScreen
import com.example.ui.games.dice.DiceScreen
import com.example.ui.games.keno.KenoScreen
import com.example.ui.games.plinko.PlinkoScreen
import com.example.ui.games.roulette.RouletteScreen
import com.example.ui.games.slots.SlotsScreen
import com.example.ui.games.videopoker.VideoPokerScreen
import com.example.ui.leaderboard.LeaderboardScreen
import com.example.ui.lobby.LobbyScreen
import com.example.ui.navigation.BottomNavTab
import com.example.ui.navigation.Screen
import com.example.ui.profile.ProfileScreen
import com.example.ui.responsible.AgeGateDialog
import com.example.ui.responsible.ResponsibleGamingScreen
import com.example.ui.wallet.WalletScreen
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CasinoApp()
            }
        }
    }
}

@Composable
fun CasinoApp() {
    val haptic = LocalHapticFeedback.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Lobby) }
    var currentBottomTab by remember { mutableStateOf(BottomNavTab.LOBBY) }
    val responsibleSettings by CasinoRepository.responsibleSettings.collectAsState()

    // Handle back button on sub-screens
    if (currentScreen != Screen.Lobby) {
        BackHandler {
            currentScreen = Screen.Lobby
            currentBottomTab = BottomNavTab.LOBBY
        }
    }

    // Age Gate Modal
    if (!responsibleSettings.isAgeGateAccepted) {
        AgeGateDialog(onAccept = {
            CasinoRepository.acceptAgeGate()
            SoundAndHapticManager.playClick(haptic)
        })
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBackground),
        containerColor = CasinoBackground,
        bottomBar = {
            // Only show bottom nav bar when on main platform screens
            if (currentScreen !is Screen.Game) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = CasinoSurface,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, CasinoCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavTab.values().forEach { tab ->
                            val isSelected = currentBottomTab == tab
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        SoundAndHapticManager.playClick(haptic)
                                        currentBottomTab = tab
                                        currentScreen = when (tab) {
                                            BottomNavTab.LOBBY -> Screen.Lobby
                                            BottomNavTab.WALLET -> Screen.Wallet
                                            BottomNavTab.FAIRNESS -> Screen.Fairness
                                            BottomNavTab.LEADERBOARD -> Screen.Leaderboard
                                            BottomNavTab.PROFILE -> Screen.Profile
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("nav_tab_${tab.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(tab.iconEmoji, fontSize = 20.sp)
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GoldPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    Screen.Lobby -> LobbyScreen(
                        onSelectGame = { gameType -> currentScreen = Screen.Game(gameType) },
                        onOpenWallet = {
                            currentBottomTab = BottomNavTab.WALLET
                            currentScreen = Screen.Wallet
                        },
                        onOpenResponsibleGaming = { currentScreen = Screen.ResponsibleGaming }
                    )

                    Screen.Wallet -> WalletScreen(
                        onBack = {
                            currentBottomTab = BottomNavTab.LOBBY
                            currentScreen = Screen.Lobby
                        }
                    )

                    Screen.Fairness -> FairnessVerifyScreen(
                        onBack = {
                            currentBottomTab = BottomNavTab.LOBBY
                            currentScreen = Screen.Lobby
                        }
                    )

                    Screen.Leaderboard -> LeaderboardScreen(
                        onBack = {
                            currentBottomTab = BottomNavTab.LOBBY
                            currentScreen = Screen.Lobby
                        }
                    )

                    Screen.Profile -> ProfileScreen(
                        onBack = {
                            currentBottomTab = BottomNavTab.LOBBY
                            currentScreen = Screen.Lobby
                        },
                        onOpenResponsibleGaming = { currentScreen = Screen.ResponsibleGaming }
                    )

                    Screen.ResponsibleGaming -> ResponsibleGamingScreen(
                        onBack = { currentScreen = Screen.Lobby }
                    )

                    is Screen.Game -> {
                        when (screen.gameType) {
                            GameType.SLOTS -> SlotsScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.BLACKJACK -> BlackjackScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.ROULETTE -> RouletteScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.BACCARAT -> BaccaratScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.CRASH -> CrashScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.DICE -> DiceScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.COIN_FLIP -> CoinFlipScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.VIDEO_POKER -> VideoPokerScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.PLINKO -> PlinkoScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                            GameType.KENO -> KenoScreen(
                                onBack = { currentScreen = Screen.Lobby },
                                onOpenWallet = { currentScreen = Screen.Wallet }
                            )
                        }
                    }
                }
            }
        }
    }
}
