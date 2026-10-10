package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.SoundAndHapticManager
import com.example.data.config.CasinoConfig
import com.example.data.model.GameType
import com.example.data.model.ProvablyFairRound
import com.example.data.repository.ProvablyFairEngine
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

fun formatChips(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(amount)
}

@Composable
fun ChipBalancePill(
    balance: Long,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .clickable {
                SoundAndHapticManager.playClick(haptic)
                onAddClick()
            }
            .testTag("chip_balance_pill"),
        color = CasinoSurfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(GoldPrimary, GoldDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🪙",
                    fontSize = 12.sp
                )
            }

            Column {
                Text(
                    text = "CHIPS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight,
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatChips(balance),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary)
                    .clickable {
                        SoundAndHapticManager.playClick(haptic)
                        onAddClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Chips",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun PlayMoneyDisclaimerBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color(0xFF1B1D28),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3347))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = GoldSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PLAY-MONEY CASINO • VIRTUAL CHIPS ONLY • NO REAL VALUE • 18+",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ChipSelectorBar(
    currentBet: Long,
    onBetSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    chips: List<Long> = listOf(10L, 25L, 50L, 100L, 250L, 500L, 1000L)
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(chips) { chipValue ->
            val isSelected = currentBet == chipValue
            val chipColor = when {
                chipValue < 50 -> ChipWhite
                chipValue < 100 -> ChipRed
                chipValue < 500 -> ChipBlue
                chipValue < 1000 -> ChipGreen
                else -> GoldPrimary
            }

            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) GoldPrimary else Color.Gray.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable {
                        SoundAndHapticManager.playChipPlace(haptic)
                        onBetSelected(chipValue)
                    }
                    .testTag("chip_value_${chipValue}"),
                color = if (isSelected) chipColor.copy(alpha = 0.25f) else CasinoSurfaceVariant,
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chipValue >= 1000) "${chipValue / 1000}K" else "$chipValue",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) GoldLight else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRulesDialog(
    gameType: GameType,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = CasinoSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GoldPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = gameType.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = CasinoSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "RTP", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "${gameType.targetRtp}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = CasinoSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "VOLATILITY", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = gameType.volatility,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldSecondary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = CasinoSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "LIMITS", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "${gameType.minBet}-${gameType.maxBet}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "RULES & PAYTABLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = CasinoConfig.getGameRules(gameType),
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                PlayMoneyDisclaimerBadge()

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(text = "Understood, Let's Play", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VerifyFairnessDialog(
    round: ProvablyFairRound?,
    onDismiss: () -> Unit
) {
    var auditResult by remember { mutableStateOf<ProvablyFairEngine.AuditResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = CasinoSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Provably Fair Audit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Every round is mathematically predetermined using HMAC-SHA256 and cannot be altered by either player or casino.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (round == null) {
                    Text(text = "No previous round recorded yet. Play a round to verify!", color = TextSecondary)
                } else {
                    AuditItem("Round ID", round.roundId)
                    AuditItem("Game", round.gameType.title)
                    AuditItem("Server Seed Hash (Pre-committed)", round.serverSeedHashed)
                    AuditItem("Secret Server Seed (Revealed)", round.serverSeedSecret)
                    AuditItem("Client Seed", round.clientSeed)
                    AuditItem("Nonce", round.nonce.toString())
                    AuditItem("Outcome HMAC-SHA256", round.outcomeHash)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isChecking = true
                            auditResult = ProvablyFairEngine.verifyAudit(
                                serverSeedSecret = round.serverSeedSecret,
                                serverSeedHashExpected = round.serverSeedHashed,
                                clientSeed = round.clientSeed,
                                nonce = round.nonce,
                                expectedOutcomeHash = round.outcomeHash
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verify Cryptographic Proof",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    auditResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (res.is100PercentFair) Color(0xFF0D3318) else Color(0xFF330D0D),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (res.is100PercentFair) EmeraldGreen else RubyRed
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (res.is100PercentFair) "VERIFIED 100% PROVABLY FAIR" else "VERIFICATION FAILED",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.is100PercentFair) EmeraldGreen else RubyRed
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Server Seed Hash match: ${if (res.isServerSeedAuthentic) "OK" else "FAIL"}\n• HMAC-SHA256 Match: ${if (res.isOutcomeAuthentic) "OK" else "FAIL"}\n• Normalized Result: ${res.reconstructedOutcomeFloat}",
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            color = CasinoSurfaceVariant,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}
