package com.example.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.model.GameCategory
import com.example.data.model.GameType
import com.example.data.repository.CasinoRepository
import com.example.ui.components.ChipBalancePill
import com.example.ui.components.PlayMoneyDisclaimerBadge
import com.example.ui.components.formatChips
import com.example.ui.theme.*

@Composable
fun LobbyScreen(
    onSelectGame: (GameType) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenResponsibleGaming: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()
    val favoriteIds by CasinoRepository.favoriteGameIds.collectAsState()
    val recentIds by CasinoRepository.recentGameIds.collectAsState()
    val isSoundOn by CasinoRepository.isSoundEnabled.collectAsState()
    val isHapticsOn by CasinoRepository.isHapticsEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GameCategory.ALL) }
    var claimMessage by remember { mutableStateOf<String?>(null) }

    val allGames = GameType.values().toList()
    val filteredGames = allGames.filter { game ->
        val matchesCategory = (selectedCategory == GameCategory.ALL) || (game.category == selectedCategory)
        val matchesSearch = game.title.contains(searchQuery, ignoreCase = true) ||
                game.subtitle.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
    ) {
        item {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GoldPrimary, GoldDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userProfile.avatarEmoji, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "GRAND ROYALE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${userProfile.vipTier.title} VIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { CasinoRepository.toggleSound() }) {
                        Icon(
                            imageVector = if (isSoundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Sound Toggle",
                            tint = if (isSoundOn) GoldPrimary else TextSecondary
                        )
                    }
                    IconButton(onClick = onOpenResponsibleGaming) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Responsible Gaming",
                            tint = EmeraldGreen
                        )
                    }
                    ChipBalancePill(balance = balance, onAddClick = onOpenWallet)
                }
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        // Daily Bonus Hero Banner
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, Brush.horizontalGradient(listOf(GoldPrimary, GoldDark, RubyRed)), RoundedCornerShape(18.dp)),
                color = Color(0xFF161928)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DAILY ROYAL TREASURY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Claim 2,500+ Free Chips",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Replenishes every 24 hours. Boosted by ${userProfile.vipTier.title} VIP tier!",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                val res = CasinoRepository.claimDailyBonus()
                                if (res.isSuccess) {
                                    SoundAndHapticManager.playBigWin(haptic)
                                    claimMessage = "Credited +${formatChips(res.getOrNull() ?: 2500L)} chips!"
                                } else {
                                    claimMessage = res.exceptionOrNull()?.message
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("CLAIM", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }

                    claimMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = msg, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lobby_search_field"),
                placeholder = { Text("Search games (e.g. Blackjack, Slots, Crash...)", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldLight) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = CasinoCardBorder,
                    focusedContainerColor = CasinoSurfaceVariant,
                    unfocusedContainerColor = CasinoSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(GameCategory.values()) { cat ->
                    val isSel = selectedCategory == cat
                    FilterChip(
                        selected = isSel,
                        onClick = {
                            selectedCategory = cat
                            SoundAndHapticManager.playClick(haptic)
                        },
                        label = { Text(cat.displayName, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = CasinoSurfaceVariant,
                            labelColor = Color.White
                        )
                    )
                }
            }
        }

        // Recently Played Section
        if (recentIds.isNotEmpty()) {
            item {
                Column {
                    Text("RECENTLY PLAYED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentIds) { gId ->
                            val g = allGames.find { it.id == gId }
                            if (g != null) {
                                Surface(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectGame(g) }
                                        .border(1.dp, CasinoCardBorder, RoundedCornerShape(12.dp)),
                                    color = CasinoSurfaceVariant
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(getGameEmoji(g), fontSize = 24.sp)
                                        Text(g.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                        Text("${g.targetRtp}% RTP", fontSize = 10.sp, color = EmeraldGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // All 10 Games Grid Section
        item {
            Text("ALL CASINO GAMES (${filteredGames.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
        }

        items(filteredGames.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { game ->
                    val isFav = favoriteIds.contains(game.id)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, CasinoCardBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                SoundAndHapticManager.playClick(haptic)
                                onSelectGame(game)
                            }
                            .testTag("game_card_${game.id}"),
                        color = CasinoSurface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E2235)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(getGameEmoji(game), fontSize = 22.sp)
                                }

                                IconButton(
                                    onClick = { CasinoRepository.toggleFavorite(game.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) RubyRed else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = game.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )

                            Text(
                                text = game.subtitle,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = EmeraldGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${game.targetRtp}% RTP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = "PLAY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldPrimary
                                )
                            }
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun getGameEmoji(game: GameType): String = when (game) {
    GameType.SLOTS -> "🎰"
    GameType.BLACKJACK -> "🃏"
    GameType.ROULETTE -> "🎡"
    GameType.BACCARAT -> "💎"
    GameType.CRASH -> "🚀"
    GameType.DICE -> "🎲"
    GameType.COIN_FLIP -> "🪙"
    GameType.VIDEO_POKER -> "♠️"
    GameType.PLINKO -> "🔺"
    GameType.KENO -> "🎱"
}
