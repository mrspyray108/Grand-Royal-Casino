package com.example.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.repository.CasinoRepository
import com.example.ui.components.PlayMoneyDisclaimerBadge
import com.example.ui.components.formatChips
import com.example.ui.theme.*

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val avatar: String,
    val chipsWon: Long,
    val vipTier: String,
    val isCurrentUser: Boolean = false
)

@Composable
fun LeaderboardScreen(
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val userProfile by CasinoRepository.userProfile.collectAsState()
    var selectedTimeframe by remember { mutableStateOf("DAILY") }

    val dailyEntries = listOf(
        LeaderboardEntry(1, "VegasViper", "🦁", 1_850_000L, "Diamond"),
        LeaderboardEntry(2, if (userProfile.isPrivacyOptedOut) "Anonymous Player" else userProfile.username, userProfile.avatarEmoji, userProfile.totalWon.coerceAtLeast(1_240_000L), userProfile.vipTier.title, isCurrentUser = true),
        LeaderboardEntry(3, "QueenAce", "👑", 980_000L, "Platinum"),
        LeaderboardEntry(4, "LuckyStrike88", "💎", 740_000L, "Platinum"),
        LeaderboardEntry(5, "RedRoulette", "🎰", 520_000L, "Gold"),
        LeaderboardEntry(6, "DiceDemon", "🎲", 410_000L, "Gold"),
        LeaderboardEntry(7, "HighRollerJack", "🎩", 350_000L, "Silver"),
        LeaderboardEntry(8, "CasinoBaron", "⭐", 290_000L, "Silver")
    )

    val weeklyEntries = listOf(
        LeaderboardEntry(1, "MonacoKing", "👑", 8_500_000L, "Diamond"),
        LeaderboardEntry(2, "VegasViper", "🦁", 6_200_000L, "Diamond"),
        LeaderboardEntry(3, "WhaleHunter", "🐋", 4_800_000L, "Diamond"),
        LeaderboardEntry(4, if (userProfile.isPrivacyOptedOut) "Anonymous Player" else userProfile.username, userProfile.avatarEmoji, userProfile.totalWon.coerceAtLeast(3_400_000L), userProfile.vipTier.title, isCurrentUser = true),
        LeaderboardEntry(5, "QueenAce", "💎", 2_900_000L, "Platinum"),
        LeaderboardEntry(6, "NeonHigh", "⚡", 2_100_000L, "Platinum"),
        LeaderboardEntry(7, "LuckyStrike88", "🍀", 1_750_000L, "Gold")
    )

    val currentList = if (selectedTimeframe == "DAILY") dailyEntries else weeklyEntries

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("ROYAL HIGH ROLLERS LEADERBOARD", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        // Timeframe Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("DAILY", "WEEKLY").forEach { tf ->
                    val isSel = selectedTimeframe == tf
                    Button(
                        onClick = {
                            selectedTimeframe = tf
                            SoundAndHapticManager.playClick(haptic)
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) GoldPrimary else CasinoSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (tf == "DAILY") "TODAY'S LEADERS" else "THIS WEEK'S LEADERS",
                            color = if (isSel) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Top 3 Podium Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 2nd Place
                    currentList.getOrNull(1)?.let { p2 ->
                        PodiumSpot(entry = p2, height = 75.dp, medal = "🥈", color = Color(0xFFC0C0C0))
                    }

                    // 1st Place
                    currentList.getOrNull(0)?.let { p1 ->
                        PodiumSpot(entry = p1, height = 95.dp, medal = "🥇", color = GoldPrimary)
                    }

                    // 3rd Place
                    currentList.getOrNull(2)?.let { p3 ->
                        PodiumSpot(entry = p3, height = 65.dp, medal = "🥉", color = Color(0xFFCD7F32))
                    }
                }
            }
        }

        itemsIndexed(currentList) { idx, entry ->
            val rank = idx + 1
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (entry.isCurrentUser) 1.5.dp else 0.5.dp,
                        color = if (entry.isCurrentUser) GoldPrimary else CasinoCardBorder,
                        shape = RoundedCornerShape(12.dp)
                    ),
                color = if (entry.isCurrentUser) Color(0xFF1E2135) else CasinoSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#$rank",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = when (rank) {
                                1 -> GoldPrimary
                                2 -> Color(0xFFC0C0C0)
                                3 -> Color(0xFFCD7F32)
                                else -> TextSecondary
                            },
                            modifier = Modifier.width(32.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2B2F44)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(entry.avatar, fontSize = 18.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (entry.isCurrentUser) GoldLight else Color.White
                                )
                                if (entry.isCurrentUser) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = GoldPrimary, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "YOU",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(entry.vipTier, fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "+${formatChips(entry.chipsWon)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = EmeraldGreen
                        )
                        Text("VIRTUAL WON", fontSize = 9.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSpot(entry: LeaderboardEntry, height: androidx.compose.ui.unit.Dp, medal: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(medal, fontSize = 20.sp)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF262A3E)),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.avatar, fontSize = 18.sp)
        }
        Text(entry.name.take(9), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${formatChips(entry.chipsWon / 1000)}k", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color.copy(alpha = 0.35f))
                .border(1.dp, color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("#${entry.rank}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}
