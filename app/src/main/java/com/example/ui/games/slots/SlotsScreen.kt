package com.example.ui.games.slots

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import com.example.audio.SoundAndHapticManager
import com.example.data.model.GameType
import com.example.data.model.ProvablyFairRound
import com.example.data.repository.CasinoRepository
import com.example.data.repository.ProvablyFairEngine
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class SlotSymbol(val emoji: String, val displayName: String, val multiplier5: Int, val isWild: Boolean = false, val isScatter: Boolean = false) {
    SEVEN("7️⃣", "Lucky 7", 50),
    DIAMOND("💎", "Diamond", 35),
    CROWN("👑", "Crown", 25),
    BELL("🔔", "Bell", 15),
    CHERRY("🍒", "Cherry", 10),
    LEMON("🍋", "Lemon", 8),
    ORANGE("🍊", "Orange", 5),
    WILD("⭐", "Wild", 50, isWild = true),
    SCATTER("🎰", "Scatter", 10, isScatter = true)
}

// 20 predefined paylines for 5 reels (row index 0, 1, 2 for reels 0..4)
val PAYLINES = listOf(
    listOf(1, 1, 1, 1, 1), // Line 1: Center horizontal
    listOf(0, 0, 0, 0, 0), // Line 2: Top horizontal
    listOf(2, 2, 2, 2, 2), // Line 3: Bottom horizontal
    listOf(0, 1, 2, 1, 0), // Line 4: V shape
    listOf(2, 1, 0, 1, 2), // Line 5: Inverted V
    listOf(0, 0, 1, 2, 2),
    listOf(2, 2, 1, 0, 0),
    listOf(1, 0, 0, 0, 1),
    listOf(1, 2, 2, 2, 1),
    listOf(0, 1, 1, 1, 0),
    listOf(2, 1, 1, 1, 2),
    listOf(0, 1, 0, 1, 0),
    listOf(2, 1, 2, 1, 2),
    listOf(1, 0, 1, 0, 1),
    listOf(1, 2, 1, 2, 1),
    listOf(0, 0, 2, 0, 0),
    listOf(2, 2, 0, 2, 2),
    listOf(0, 2, 0, 2, 0),
    listOf(2, 0, 2, 0, 2),
    listOf(1, 0, 2, 0, 1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotsScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betPerLine by remember { mutableLongStateOf(5L) }
    val totalBet = betPerLine * 20L

    var isSpinning by remember { mutableStateOf(false) }
    var freeSpinsRemaining by remember { mutableIntStateOf(0) }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf("Set bet and spin the reels!") }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }
    var winningLinesCount by remember { mutableIntStateOf(0) }

    // 5 reels x 3 rows matrix
    var grid by remember {
        mutableStateOf(
            List(5) {
                listOf(SlotSymbol.SEVEN, SlotSymbol.DIAMOND, SlotSymbol.CROWN)
            }
        )
    }

    fun spinReels() {
        if (isSpinning) return
        val isFree = freeSpinsRemaining > 0
        if (!isFree && balance < totalBet) {
            statusMessage = "Insufficient chips! Please get more chips in wallet."
            return
        }

        coroutineScope.launch {
            isSpinning = true
            statusMessage = if (isFree) "Free Spin Active! (3x Multiplier)" else "Spinning..."
            lastWinAmount = 0L
            winningLinesCount = 0

            val roundId = UUID.randomUUID().toString()
            if (!isFree) {
                val betResult = CasinoRepository.placeBet(
                    gameType = GameType.SLOTS,
                    amount = totalBet,
                    idempotencyKey = "slots_bet_$roundId",
                    note = "20 Lines @ $betPerLine chips"
                )
                if (betResult.isFailure) {
                    statusMessage = betResult.exceptionOrNull()?.message ?: "Bet failed"
                    isSpinning = false
                    return@launch
                }
            } else {
                freeSpinsRemaining -= 1
            }

            SoundAndHapticManager.playSpin(haptic)

            // Provably fair generation
            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            // Reel spinning animation
            val allSymbols = SlotSymbol.values()
            for (step in 0..12) {
                grid = List(5) {
                    List(3) { allSymbols.random() }
                }
                delay(80)
            }

            // Generate deterministic result from HMAC bytes
            val finalGrid = MutableList(5) { reel ->
                MutableList(3) { row ->
                    val offset = (reel * 3 + row) * 2
                    val symbolIndex = ProvablyFairEngine.hmacToInt(outcomeHash, 0, allSymbols.size - 1, offset)
                    allSymbols[symbolIndex]
                }
            }
            grid = finalGrid

            // Calculate wins across 20 paylines
            var totalPayout = 0L
            var winsFound = 0
            val lineMultiplier = if (isFree) 3 else 1

            for (line in PAYLINES) {
                val firstSym = finalGrid[0][line[0]]
                var matchCount = 1
                for (reel in 1..4) {
                    val currentSym = finalGrid[reel][line[reel]]
                    if (currentSym == firstSym || currentSym.isWild || firstSym.isWild) {
                        matchCount++
                    } else {
                        break
                    }
                }

                if (matchCount >= 3) {
                    winsFound++
                    val payoutBase = when (matchCount) {
                        3 -> firstSym.multiplier5 / 5
                        4 -> firstSym.multiplier5 / 2
                        5 -> firstSym.multiplier5
                        else -> 0
                    }.coerceAtLeast(1)
                    totalPayout += (betPerLine * payoutBase * lineMultiplier)
                }
            }

            // Scatter check for Free Spins
            var scatterCount = 0
            for (reel in 0..4) {
                for (row in 0..2) {
                    if (finalGrid[reel][row].isScatter) scatterCount++
                }
            }

            if (scatterCount >= 3) {
                freeSpinsRemaining += 10
                statusMessage = "🎉 3+ SCATTERS! Awarded 10 FREE SPINS (3x)!"
                SoundAndHapticManager.playBigWin(haptic)
            }

            winningLinesCount = winsFound
            if (totalPayout > 0) {
                lastWinAmount = totalPayout
                CasinoRepository.creditPayout(
                    gameType = GameType.SLOTS,
                    amount = totalPayout,
                    idempotencyKey = "slots_payout_$roundId",
                    note = "Slot Win: $winsFound line(s) hit"
                )
                SoundAndHapticManager.playWin(haptic)
                if (scatterCount < 3) {
                    statusMessage = "🏆 BIG WIN! Paid ${formatChips(totalPayout)} chips!"
                }
            } else if (scatterCount < 3) {
                statusMessage = if (freeSpinsRemaining > 0) "Free Spins: $freeSpinsRemaining left" else "Try again! Match 3+ symbols."
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.SLOTS,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Lines: $winsFound | Win: $totalPayout chips | Scatters: $scatterCount"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isSpinning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.SLOTS.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("20 Paylines • Wilds & Scatters", fontSize = 11.sp, color = GoldLight)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showFairnessDialog = true }) {
                        Icon(Icons.Default.Security, contentDescription = "Fairness", tint = EmeraldGreen)
                    }
                    IconButton(onClick = { showRulesDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Rules", tint = GoldPrimary)
                    }
                    ChipBalancePill(balance = balance, onAddClick = onOpenWallet)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CasinoSurface)
            )
        },
        containerColor = CasinoBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PlayMoneyDisclaimerBadge()

            // Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = if (lastWinAmount > 0) Color(0xFF1B3820) else CasinoSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (lastWinAmount > 0) EmeraldGreen else CasinoCardBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = statusMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (lastWinAmount > 0) EmeraldGreen else Color.White
                        )
                        if (freeSpinsRemaining > 0) {
                            Text(
                                text = "FREE SPINS REMAINING: $freeSpinsRemaining",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldPrimary
                            )
                        }
                    }
                    if (lastWinAmount > 0) {
                        Text(
                            text = "+${formatChips(lastWinAmount)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                    }
                }
            }

            // 5-Reel Slot Frame
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(16.dp)),
                color = Color(0xFF0F101A)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..4) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF171926))
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (row in 0..2) {
                                    val sym = grid[col][row]
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    sym.isWild -> Brush.radialGradient(listOf(GoldPrimary.copy(alpha = 0.5f), Color.Transparent))
                                                    sym.isScatter -> Brush.radialGradient(listOf(RubyRed.copy(alpha = 0.5f), Color.Transparent))
                                                    else -> Brush.radialGradient(listOf(CasinoSurfaceVariant, Color(0xFF171926)))
                                                }
                                            )
                                            .border(
                                                width = if (sym.isWild || sym.isScatter) 1.5.dp else 0.5.dp,
                                                color = if (sym.isWild) GoldPrimary else if (sym.isScatter) RubyRed else Color(0xFF2E3347),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sym.emoji,
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bet Controls and Spin Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LINE BET", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    if (betPerLine > 1) {
                                        betPerLine -= 1
                                        SoundAndHapticManager.playClick(haptic)
                                    }
                                },
                                enabled = !isSpinning && betPerLine > 1,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape
                            ) {
                                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$betPerLine",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (betPerLine < 250) {
                                        betPerLine += 5
                                        SoundAndHapticManager.playClick(haptic)
                                    }
                                },
                                enabled = !isSpinning && betPerLine < 250,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL BET", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${formatChips(totalBet)} CHIPS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text("20 Fixed Lines", fontSize = 10.sp, color = GoldLight)
                    }
                }

                Button(
                    onClick = { spinReels() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("slot_spin_button"),
                    enabled = !isSpinning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (freeSpinsRemaining > 0) EmeraldGreen else GoldPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSpinning) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SPINNING...", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (freeSpinsRemaining > 0) "FREE SPIN ($freeSpinsRemaining LEFT)" else "SPIN (${formatChips(totalBet)} CHIPS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.SLOTS, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
