package com.example.ui.responsible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
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

@Composable
fun ResponsibleGamingScreen(
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val settings by CasinoRepository.responsibleSettings.collectAsState()

    var lossLimitInput by remember { mutableStateOf(if (settings.dailyLossLimit > 0) settings.dailyLossLimit.toString() else "") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val elapsedMinutes = ((System.currentTimeMillis() - settings.sessionStartTimeMillis) / (1000 * 60)).coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                Text("RESPONSIBLE GAMING & COMPLIANCE", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        // Active Session Timer Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ACTIVE PLAY SESSION TIMER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                        Text("$elapsedMinutes minutes active in this session", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Take regular breaks every 30-45 minutes.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        // Daily Loss Limit Control
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DAILY LOSS LIMIT CONTROL", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    Text(
                        text = "Set a maximum virtual chips loss limit per day. When reached, further bets are paused.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lossLimitInput,
                            onValueChange = { lossLimitInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Chips Limit (e.g. 5000)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = CasinoCardBorder
                            )
                        )

                        Button(
                            onClick = {
                                val limit = lossLimitInput.toLongOrNull() ?: 0L
                                CasinoRepository.setDailyLossLimit(limit)
                                SoundAndHapticManager.playClick(haptic)
                                statusMessage = if (limit > 0) "Daily loss limit set to ${formatChips(limit)} chips." else "Daily loss limit disabled."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (settings.dailyLossLimit > 0) {
                        Text(
                            text = "Current loss today: ${formatChips(settings.currentDailyLoss)} / ${formatChips(settings.dailyLossLimit)} chips",
                            fontSize = 11.sp,
                            color = if (settings.currentDailyLoss >= settings.dailyLossLimit) RubyRed else EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Self-Exclusion Tool
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF261818),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RubyRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = RubyRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SELF-EXCLUSION / TAKE A BREAK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RubyRed)
                    }

                    Text(
                        text = "Lock betting access for a set cooling-off period to enforce healthy gaming habits.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (settings.selfExclusionUntilMillis > System.currentTimeMillis()) {
                        val remainingHours = (settings.selfExclusionUntilMillis - System.currentTimeMillis()) / (1000 * 60 * 60) + 1
                        Text(
                            text = "🔒 SELF-EXCLUSION ACTIVE: Play blocked for next $remainingHours hour(s).",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RubyRed
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                CasinoRepository.cancelSelfExclusion()
                                SoundAndHapticManager.playClick(haptic)
                                statusMessage = "Self-exclusion lifted."
                            }
                        ) {
                            Text("End Exclusion Early (Simulation Mode)", color = TextSecondary, fontSize = 11.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    CasinoRepository.setSelfExclusion(24)
                                    SoundAndHapticManager.playClick(haptic)
                                    statusMessage = "Self-exclusion set for 24 hours."
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("24 HOURS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    CasinoRepository.setSelfExclusion(168)
                                    SoundAndHapticManager.playClick(haptic)
                                    statusMessage = "Self-exclusion set for 7 days."
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("7 DAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    CasinoRepository.setSelfExclusion(720)
                                    SoundAndHapticManager.playClick(haptic)
                                    statusMessage = "Self-exclusion set for 30 days."
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("30 DAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        statusMessage?.let { msg ->
            item {
                Text(text = msg, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
            }
        }

        // Help Resources & Legal Links
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("HELP & SUPPORT RESOURCES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• National Problem Gambling Helpline: 1-800-522-4700 (24/7 Confidential)", fontSize = 11.sp, color = TextPrimary)
                    Text("• Gamblers Anonymous: www.gamblersanonymous.org", fontSize = 11.sp, color = TextPrimary)
                    Text("• GamCare (UK): 0808 8020 133", fontSize = 11.sp, color = TextPrimary)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showTermsDialog = true }) {
                            Text("Terms of Service", color = GoldLight, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showPrivacyDialog = true }) {
                            Text("Privacy Policy", color = GoldLight, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Grand Royale is an entertainment platform offering play-money casino simulations exclusively. All chips, coins, and balances have ZERO cash value and cannot be redeemed, transferred, or exchanged for real money, goods, or services. Users must be at least 18 years old.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Accept")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Grand Royale respects your privacy. All game seeds, statistics, and transactions are stored locally on your device or in zero-knowledge ledger logs. You can opt out of leaderboard display in your Profile settings at any time.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
}
