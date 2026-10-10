package com.example.ui.games.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RocketLaunch
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

data class SimulatedPlayer(
    val name: String,
    val bet: Long,
    val cashedOutAt: Double? = null
)

enum class CrashStage {
    WAITING_FOR_BETS,
    FLYING,
    CRASHED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(25L) }
    var currentMultiplier by remember { mutableDoubleStateOf(1.00) }
    var crashPoint by remember { mutableDoubleStateOf(2.50) }
    var stage by remember { mutableStateOf(CrashStage.WAITING_FOR_BETS) }
    var hasCashedOut by remember { mutableStateOf(false) }
    var userCashedAt by remember { mutableDoubleStateOf(0.0) }
    var userProfit by remember { mutableLongStateOf(0L) }
    var pastCrashes by remember { mutableStateOf(listOf(1.42, 3.88, 1.15, 7.20, 2.10, 1.95)) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }
    var activeRoundId by remember { mutableStateOf("") }

    var simulatedRoster by remember {
        mutableStateOf(
            listOf(
                SimulatedPlayer("CyberAce", 100L),
                SimulatedPlayer("GoldVegas", 250L),
                SimulatedPlayer("LuckyWhale", 500L),
                SimulatedPlayer("NeonRider", 50L)
            )
        )
    }

    fun launchRocket() {
        if (stage == CrashStage.FLYING) return
        if (balance < betAmount) return

        coroutineScope.launch {
            activeRoundId = UUID.randomUUID().toString()
            hasCashedOut = false
            userCashedAt = 0.0
            userProfit = 0L

            val betRes = CasinoRepository.placeBet(
                gameType = GameType.CRASH,
                amount = betAmount,
                idempotencyKey = "crash_bet_$activeRoundId",
                note = "Rocket Crash bet: $betAmount chips"
            )
            if (betRes.isFailure) return@launch

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            // Provably fair crash point formula: 98 / (100 - (f * 98))
            val randFloat = ProvablyFairEngine.hmacToFloat(outcomeHash)
            val computedCrash = if (randFloat < 0.04) {
                1.00 // Instant crash (4% house instant crash)
            } else {
                (0.98 / (1.0 - randFloat * 0.96)).coerceIn(1.01, 88.0)
            }
            crashPoint = "%.2f".format(computedCrash).toDouble()

            simulatedRoster = listOf(
                SimulatedPlayer("CyberAce", 100L),
                SimulatedPlayer("GoldVegas", 250L),
                SimulatedPlayer("LuckyWhale", 500L),
                SimulatedPlayer("NeonRider", 50L)
            )

            stage = CrashStage.FLYING
            currentMultiplier = 1.00
            SoundAndHapticManager.playSpin(haptic)

            var mult = 1.00
            while (mult < crashPoint) {
                delay(60)
                val step = if (mult < 2.0) 0.02 else if (mult < 5.0) 0.05 else 0.12
                mult += step
                currentMultiplier = "%.2f".format(mult).toDouble()

                // Random simulated player cash outs
                simulatedRoster = simulatedRoster.map { p ->
                    if (p.cashedOutAt == null && mult > (1.2 + (p.bet % 5) * 0.4) && Math.random() < 0.2) {
                        p.copy(cashedOutAt = currentMultiplier)
                    } else p
                }
            }

            // CRASHED!
            stage = CrashStage.CRASHED
            currentMultiplier = crashPoint
            pastCrashes = (listOf(crashPoint) + pastCrashes).take(8)

            if (!hasCashedOut) {
                SoundAndHapticManager.playCrash()
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.CRASH,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Crashed @ ${crashPoint}x | Cashed: ${if (hasCashedOut) "${userCashedAt}x" else "BUST"}"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
        }
    }

    fun cashOut() {
        if (stage != CrashStage.FLYING || hasCashedOut) return
        hasCashedOut = true
        userCashedAt = currentMultiplier
        val winAmount = (betAmount * userCashedAt).toLong()
        userProfit = winAmount

        CasinoRepository.creditPayout(
            gameType = GameType.CRASH,
            amount = winAmount,
            idempotencyKey = "crash_payout_$activeRoundId",
            note = "Cashed out at ${userCashedAt}x"
        )
        SoundAndHapticManager.playWin(haptic)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.CRASH.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Multiplier Rising • Cash Out Before Crash", fontSize = 11.sp, color = GoldLight)
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
            Column {
                PlayMoneyDisclaimerBadge()

                Spacer(modifier = Modifier.height(6.dp))

                // History Multiplier Pills
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pastCrashes) { valM ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                            color = if (valM >= 2.0) EmeraldGreen.copy(alpha = 0.2f) else RubyRed.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (valM >= 2.0) EmeraldGreen else RubyRed)
                        ) {
                            Text(
                                text = "${valM}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (valM >= 2.0) EmeraldGreen else RubyRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Rocket Flight Center Canvas
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF0D111E)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (stage == CrashStage.CRASHED) {
                            Text("💥 CRASHED", fontSize = 18.sp, fontWeight = FontWeight.Black, color = RubyRed)
                            Text(
                                text = "@ ${currentMultiplier}x",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = RubyRed
                            )
                        } else if (stage == CrashStage.FLYING) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "${currentMultiplier}x",
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Black,
                                color = if (hasCashedOut) EmeraldGreen else Color.White
                            )
                            if (hasCashedOut) {
                                Text(
                                    text = "CASHED OUT @ ${userCashedAt}x (+${formatChips(userProfit)} CHIPS)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldGreen
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "1.00x",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = TextSecondary
                            )
                            Text("Ready for Launch", fontSize = 13.sp, color = GoldLight)
                        }
                    }
                }
            }

            // Live Simulated Players List
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                color = CasinoSurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("LIVE LOBBY PLAYERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        simulatedRoster.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(p.name, fontSize = 10.sp, color = TextPrimary)
                                Text("${p.bet}", fontSize = 10.sp, color = GoldLight, fontWeight = FontWeight.Bold)
                                Text(
                                    text = p.cashedOutAt?.let { "${it}x" } ?: if (stage == CrashStage.CRASHED) "💥" else "...",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (p.cashedOutAt != null) EmeraldGreen else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stage == CrashStage.FLYING && !hasCashedOut) {
                    Button(
                        onClick = { cashOut() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("crash_cashout_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "CASH OUT (+${formatChips((betAmount * currentMultiplier).toLong())} CHIPS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

                    Button(
                        onClick = { launchRocket() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("crash_launch_button"),
                        enabled = stage != CrashStage.FLYING,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "LAUNCH ROCKET (${formatChips(betAmount)} CHIPS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.CRASH, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
