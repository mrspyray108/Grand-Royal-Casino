package com.example.ui.games.dice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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

enum class DiceRollMode {
    ROLL_OVER,
    ROLL_UNDER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(50L) }
    var targetSlider by remember { mutableFloatStateOf(50.0f) }
    var rollMode by remember { mutableStateOf(DiceRollMode.ROLL_OVER) }
    var rolledValue by remember { mutableStateOf<Double?>(null) }
    var isRolling by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Adjust target and press ROLL DICE.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }

    // Win chance calculation
    val winProbability = if (rollMode == DiceRollMode.ROLL_OVER) {
        (100.0 - targetSlider).coerceIn(1.0, 98.0)
    } else {
        targetSlider.toDouble().coerceIn(1.0, 98.0)
    }

    // Payout multiplier with 98% target RTP: Multiplier = (98 / WinChance)
    val multiplier = "%.4f".format(98.0 / winProbability).toDouble()

    fun rollDice() {
        if (isRolling) return
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            isRolling = true
            statusMessage = "Rolling cryptographically fair dice..."
            lastWinAmount = 0L

            val roundId = UUID.randomUUID().toString()
            val betRes = CasinoRepository.placeBet(
                gameType = GameType.DICE,
                amount = betAmount,
                idempotencyKey = "dice_bet_$roundId",
                note = "Dice ${rollMode.name} ${targetSlider.toInt()}"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isRolling = false
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            // Deterministic float outcome from HMAC [0.00 to 100.00]
            val outcomeNorm = ProvablyFairEngine.hmacToFloat(outcomeHash)
            val finalRoll = "%.2f".format(outcomeNorm * 100.0).toDouble()

            for (step in 1..10) {
                rolledValue = "%.2f".format(Math.random() * 100.0).toDouble()
                delay(40)
            }
            rolledValue = finalRoll

            val won = if (rollMode == DiceRollMode.ROLL_OVER) {
                finalRoll > targetSlider
            } else {
                finalRoll < targetSlider
            }

            if (won) {
                val payout = (betAmount * multiplier).toLong()
                lastWinAmount = payout
                CasinoRepository.creditPayout(
                    gameType = GameType.DICE,
                    amount = payout,
                    idempotencyKey = "dice_payout_$roundId",
                    note = "Dice Won with $finalRoll"
                )
                SoundAndHapticManager.playWin(haptic)
                statusMessage = "🎉 WIN! Rolled $finalRoll! Paid ${formatChips(payout)} chips!"
            } else {
                statusMessage = "Rolled $finalRoll. Missed target. Better luck next roll!"
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.DICE,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Rolled $finalRoll | Target ${targetSlider.toInt()} (${rollMode.name}) | ${if (won) "WON" else "LOST"}"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isRolling = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.DICE.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Interactive Slider • 98% Provably Fair RTP", fontSize = 11.sp, color = GoldLight)
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

            // Big Dice Roll Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF141726)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "ROLLED NUMBER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = rolledValue?.let { "%.2f".format(it) } ?: "50.00",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            lastWinAmount > 0 -> EmeraldGreen
                            rolledValue != null -> RubyRed
                            else -> GoldLight
                        }
                    )

                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Stats Cards: Multiplier & Win Chance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = CasinoSurfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MULTIPLIER", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("${multiplier}x", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldLight)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = CasinoSurfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WIN CHANCE", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(winProbability)}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = EmeraldGreen)
                    }
                }
            }

            // Roll Over / Roll Under Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        rollMode = DiceRollMode.ROLL_OVER
                        SoundAndHapticManager.playClick(haptic)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rollMode == DiceRollMode.ROLL_OVER) GoldPrimary else CasinoSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "ROLL OVER",
                        color = if (rollMode == DiceRollMode.ROLL_OVER) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        rollMode = DiceRollMode.ROLL_UNDER
                        SoundAndHapticManager.playClick(haptic)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rollMode == DiceRollMode.ROLL_UNDER) GoldPrimary else CasinoSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "ROLL UNDER",
                        color = if (rollMode == DiceRollMode.ROLL_UNDER) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Target Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TARGET: ${targetSlider.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    Text(
                        if (rollMode == DiceRollMode.ROLL_OVER) "Needs > ${targetSlider.toInt()}" else "Needs < ${targetSlider.toInt()}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Slider(
                    value = targetSlider,
                    onValueChange = { targetSlider = it },
                    valueRange = 2f..98f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = EmeraldGreen,
                        inactiveTrackColor = RubyRed
                    )
                )
            }

            // Bet Selector & Roll Button
            ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

            Button(
                onClick = { rollDice() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("dice_roll_button"),
                enabled = !isRolling,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isRolling) "ROLLING..." else "ROLL DICE (${formatChips(betAmount)} CHIPS)",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.DICE, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
