package com.example.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.config.CasinoConfig
import com.example.data.model.ChipStorePack
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerType
import com.example.data.repository.CasinoRepository
import com.example.data.repository.ReconciliationReport
import com.example.ui.components.PlayMoneyDisclaimerBadge
import com.example.ui.components.formatChips
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val balance by CasinoRepository.balance.collectAsState()
    val ledgerEntries by CasinoRepository.ledgerEntries.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var auditReport by remember { mutableStateOf<ReconciliationReport?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    val filteredEntries = when (selectedFilter) {
        "BETS" -> ledgerEntries.filter { it.type == LedgerType.BET }
        "WINS" -> ledgerEntries.filter { it.type == LedgerType.PAYOUT }
        "BONUSES" -> ledgerEntries.filter { it.type == LedgerType.DAILY_BONUS || it.type == LedgerType.WELCOME_BONUS || it.type == LedgerType.STORE_PACK }
        else -> ledgerEntries
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("ROYAL WALLET & LEDGER", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        // Main Balance Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.horizontalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF141726)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VIRTUAL PLAY-CHIPS BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${formatChips(balance)} CHIPS",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Play-money balance only • Zero real-cash value",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val res = CasinoRepository.claimDailyBonus()
                                if (res.isSuccess) {
                                    SoundAndHapticManager.playBigWin(haptic)
                                    notificationMessage = "Claimed +${formatChips(res.getOrNull() ?: 2500L)} daily chips!"
                                } else {
                                    notificationMessage = res.exceptionOrNull()?.message
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("CLAIM DAILY BONUS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                auditReport = CasinoRepository.reconcileBalance()
                                SoundAndHapticManager.playClick(haptic)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AUDIT LEDGER", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    notificationMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = msg, fontSize = 12.sp, color = GoldLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Ledger Audit Dialog / Card
        auditReport?.let { report ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (report.isReconciled) Color(0xFF0F301C) else Color(0xFF381010),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (report.isReconciled) EmeraldGreen else RubyRed)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LEDGER RECONCILIATION AUDIT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = report.details, fontSize = 12.sp, color = Color.White)
                        Text(
                            text = "Audited Transactions: ${report.totalTransactions} | Sum: ${formatChips(report.sumOfLedgerEntries)} chips",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Virtual Chip Store Packs
        item {
            Text("VIRTUAL CHIP PACKS (FREE TEST CLAIMS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
        }

        items(CasinoConfig.STORE_PACKS) { pack ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, CasinoCardBorder, RoundedCornerShape(14.dp)),
                color = CasinoSurface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pack.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            pack.badge?.let { b ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(color = RubyRed, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = b,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${formatChips(pack.chips)} + ${formatChips(pack.bonusChips)} Bonus Chips",
                            fontSize = 12.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text("100% Free Play Money", fontSize = 10.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            CasinoRepository.purchaseStorePack(pack)
                            SoundAndHapticManager.playBigWin(haptic)
                            notificationMessage = "Added ${formatChips(pack.chips + pack.bonusChips)} chips!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CLAIM FREE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Transaction History Section
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRANSACTION LEDGER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
                    Text("${filteredEntries.size} ENTRIES", fontSize = 11.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "BETS", "WINS", "BONUSES").forEach { f ->
                        val isSel = selectedFilter == f
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedFilter = f },
                            label = { Text(f, fontSize = 11.sp) },
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
        }

        items(filteredEntries.take(30)) { entry ->
            val dateStr = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(entry.timestamp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                color = CasinoSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = when (entry.type) {
                                    LedgerType.PAYOUT -> EmeraldGreen.copy(alpha = 0.2f)
                                    LedgerType.BET -> RubyRed.copy(alpha = 0.2f)
                                    else -> GoldPrimary.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = entry.type.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (entry.type) {
                                        LedgerType.PAYOUT -> EmeraldGreen
                                        LedgerType.BET -> RubyRed
                                        else -> GoldPrimary
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(entry.note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                        }
                        Text(
                            text = "$dateStr • Key: ${entry.idempotencyKey.take(16)}...",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (entry.amount >= 0) "+${formatChips(entry.amount)}" else formatChips(entry.amount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.amount >= 0) EmeraldGreen else RubyRed
                        )
                        Text(
                            text = "Bal: ${formatChips(entry.balanceAfter)}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
