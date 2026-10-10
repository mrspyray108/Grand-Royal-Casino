package com.example.ui.games.coinflip

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

enum class CoinSide(val emoji: String, val label: String) {
    HEADS("👑", "HEADS"),
    TAILS("⭐", "TAILS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinFlipScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(25L) }
    var chosenSide by remember { mutableStateOf(CoinSide.HEADS) }
    var currentLandedSide by remember { mutableStateOf(CoinSide.HEADS) }
    var isFlipping by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var accumulatedMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streakHistory by remember { mutableStateOf(listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.HEADS, CoinSide.HEADS)) }
    var statusMessage by remember { mutableStateOf("Pick Heads or Tails and Flip!") }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }
    var activeRoundId by remember { mutableStateOf("") }

    val rotation = remember { Animatable(0f) }

    fun flipCoin() {
        if (isFlipping) return
        if (currentStreak == 0 && balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            isFlipping = true
            statusMessage = "Flipping coin..."

            if (currentStreak == 0) {
                activeRoundId = UUID.randomUUID().toString()
                val betRes = CasinoRepository.placeBet(
                    gameType = GameType.COIN_FLIP,
                    amount = betAmount,
                    idempotencyKey = "coin_bet_$activeRoundId",
                    note = "Coin Flip $chosenSide bet: $betAmount chips"
                )
                if (betRes.isFailure) {
                    statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                    isFlipping = false
                    return@launch
                }
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            // Determine outcome (0 = Heads, 1 = Tails)
            val resultSide = if (ProvablyFairEngine.hmacToFloat(outcomeHash) < 0.50) CoinSide.HEADS else CoinSide.TAILS

            // Animate spin rotation
            rotation.animateTo(
                targetValue = rotation.value + 1440f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )

            currentLandedSide = resultSide
            streakHistory = (listOf(resultSide) + streakHistory).take(10)

            if (resultSide == chosenSide) {
                currentStreak += 1
                accumulatedMultiplier *= 1.95
                SoundAndHapticManager.playWin(haptic)
                val currentPotentialChips = (betAmount * accumulatedMultiplier).toLong()
                statusMessage = "🎉 HIT! Streak: $currentStreak! Current Value: ${formatChips(currentPotentialChips)} chips!"
            } else {
                statusMessage = "Landed on ${resultSide.label}. Streak reset! Better luck next time."
                currentStreak = 0
                accumulatedMultiplier = 1.0
                SoundAndHapticManager.playCrash()
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.COIN_FLIP,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Landed: ${resultSide.label} | Picked: ${chosenSide.label} | Streak: $currentStreak"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isFlipping = false
        }
    }

    fun cashOutStreak() {
        if (currentStreak == 0 || isFlipping) return
        val totalWin = (betAmount * accumulatedMultiplier).toLong()
        CasinoRepository.creditPayout(
            gameType = GameType.COIN_FLIP,
            amount = totalWin,
            idempotencyKey = "coin_payout_${UUID.randomUUID()}",
            note = "Cashed Out Streak of $currentStreak ($totalWin chips)"
        )
        SoundAndHapticManager.playBigWin(haptic)
        statusMessage = "👑 CASHED OUT STREAK! Credited ${formatChips(totalWin)} chips!"
        currentStreak = 0
        accumulatedMultiplier = 1.0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.COIN_FLIP.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Pick Heads or Tails • Consecutive Win Streaks", fontSize = 11.sp, color = GoldLight)
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
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayMoneyDisclaimerBadge()

            // Streak History Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECENT FLIPS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(streakHistory) { side ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (side == CoinSide.HEADS) GoldPrimary else DiamondCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(side.emoji, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Big 3D Coin Visual
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF131524)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .rotate(rotation.value)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldPrimary, GoldDark)
                                )
                            )
                            .border(4.dp, GoldLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentLandedSide.emoji, fontSize = 38.sp)
                            Text(currentLandedSide.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                }
            }

            // Streak & Multiplier Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (currentStreak > 0) Color(0xFF1B3820) else CasinoSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (currentStreak > 0) EmeraldGreen else CasinoCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ACTIVE STREAK", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("$currentStreak WINS IN A ROW", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GoldLight)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("MULTIPLIER", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(accumulatedMultiplier)}x", fontSize = 16.sp, fontWeight = FontWeight.Black, color = EmeraldGreen)
                    }
                }
            }

            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Heads / Tails Choice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoinSide.values().forEach { side ->
                    val isSel = chosenSide == side
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) GoldPrimary else Color(0xFF2A2E44),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                chosenSide = side
                                SoundAndHapticManager.playClick(haptic)
                            },
                        color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else CasinoSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(side.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(side.label, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (isSel) GoldPrimary else Color.White)
                        }
                    }
                }
            }

            if (currentStreak == 0) {
                ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })
            }

            // Action Buttons (Flip or Cash Out Streak)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentStreak > 0) {
                    Button(
                        onClick = { cashOutStreak() },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "TAKE (${formatChips((betAmount * accumulatedMultiplier).toLong())})",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Button(
                    onClick = { flipCoin() },
                    modifier = Modifier.weight(1f).height(54.dp).testTag("coin_flip_button"),
                    enabled = !isFlipping,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isFlipping) "FLIPPING..." else if (currentStreak > 0) "DOUBLE OR NOTHING" else "FLIP COIN",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.COIN_FLIP, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
