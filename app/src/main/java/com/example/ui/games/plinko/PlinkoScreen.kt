package com.example.ui.games.plinko

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
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

enum class PlinkoRisk(val label: String, val multipliers: List<Double>) {
    LOW("LOW RISK", listOf(5.6, 2.1, 1.1, 1.0, 0.5, 1.0, 1.1, 2.1, 5.6)),
    MEDIUM("MEDIUM RISK", listOf(13.0, 3.0, 1.3, 0.7, 0.4, 0.7, 1.3, 3.0, 13.0)),
    HIGH("HIGH RISK", listOf(29.0, 4.0, 1.5, 0.3, 0.2, 0.3, 1.5, 4.0, 29.0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlinkoScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(25L) }
    var selectedRisk by remember { mutableStateOf(PlinkoRisk.MEDIUM) }
    var isDropping by remember { mutableStateOf(false) }
    var landedBucketIndex by remember { mutableStateOf<Int?>(null) }
    var ballPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) } // normalized 0..1 x, y
    var statusMessage by remember { mutableStateOf("Choose Risk, adjust bet, and DROP BALL.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }

    fun dropBall() {
        if (isDropping) return
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            isDropping = true
            landedBucketIndex = null
            lastWinAmount = 0L
            statusMessage = "Ball bouncing down the pyramid..."

            val roundId = UUID.randomUUID().toString()
            val betRes = CasinoRepository.placeBet(
                gameType = GameType.PLINKO,
                amount = betAmount,
                idempotencyKey = "plinko_bet_$roundId",
                note = "Plinko ${selectedRisk.label}: $betAmount chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isDropping = false
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            // 8 rows of peg deflections (left=0, right=1)
            val rows = 8
            var currentX = 0.5f
            var rightBounces = 0

            ballPosition = Pair(0.5f, 0.05f)

            for (r in 0 until rows) {
                delay(120)
                val bounceFloat = ProvablyFairEngine.hmacToFloat(outcomeHash, r * 4)
                val bounceRight = bounceFloat >= 0.50
                if (bounceRight) rightBounces++

                val spread = (r + 1) * 0.055f
                val targetX = 0.5f + ((rightBounces.toFloat() / (r + 1).toFloat()) - 0.5f) * spread * 2f
                val targetY = 0.1f + (r + 1) * 0.1f

                ballPosition = Pair(targetX.coerceIn(0.08f, 0.92f), targetY)
                SoundAndHapticManager.playChipPlace(haptic)
            }

            delay(100)
            val bucketIndex = rightBounces.coerceIn(0, selectedRisk.multipliers.size - 1)
            landedBucketIndex = bucketIndex
            ballPosition = null

            val mult = selectedRisk.multipliers[bucketIndex]
            val payout = (betAmount * mult).toLong()
            lastWinAmount = payout

            if (payout > 0) {
                CasinoRepository.creditPayout(
                    gameType = GameType.PLINKO,
                    amount = payout,
                    idempotencyKey = "plinko_payout_$roundId",
                    note = "Plinko ${mult}x Bucket Payout"
                )
                if (mult >= 2.0) SoundAndHapticManager.playWin(haptic) else SoundAndHapticManager.playClick(haptic)
            }

            statusMessage = "🎯 Landed in ${mult}x Bucket! Paid ${formatChips(payout)} chips!"

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.PLINKO,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Risk: ${selectedRisk.label} | Bucket: ${mult}x | Payout: $payout"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isDropping = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.PLINKO.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Pyramid Deflection • Up to 29x Payout", fontSize = 11.sp, color = GoldLight)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayMoneyDisclaimerBadge()

            // Plinko Pyramid Board
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(16.dp)),
                color = Color(0xFF10121F)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Draw pegs in pyramid formation
                        val totalRows = 8
                        for (r in 0..totalRows) {
                            val pegCount = r + 3
                            val rowY = (r + 1) * (canvasHeight * 0.85f / (totalRows + 2))
                            val rowWidth = (pegCount - 1) * 26.dp.toPx()
                            val startX = (canvasWidth - rowWidth) / 2f

                            for (p in 0 until pegCount) {
                                val pegX = startX + p * 26.dp.toPx()
                                drawCircle(
                                    color = Color(0xFF888B9E),
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(pegX, rowY)
                                )
                            }
                        }

                        // Draw active falling ball
                        ballPosition?.let { (nx, ny) ->
                            drawCircle(
                                color = GoldPrimary,
                                radius = 7.dp.toPx(),
                                center = Offset(nx * canvasWidth, ny * canvasHeight)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(nx * canvasWidth, ny * canvasHeight)
                            )
                        }
                    }

                    // Multiplier Buckets at Bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp, start = 6.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        selectedRisk.multipliers.forEachIndexed { idx, mult ->
                            val isHit = landedBucketIndex == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            isHit -> GoldPrimary
                                            mult >= 5.0 -> RubyRed
                                            mult >= 2.0 -> Color(0xFFFF9800)
                                            else -> Color(0xFF232738)
                                        }
                                    )
                                    .border(
                                        width = if (isHit) 2.dp else 0.5.dp,
                                        color = if (isHit) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mult}x",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHit) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (lastWinAmount > 0) EmeraldGreen else Color.White
            )

            // Risk Selector Buttons
            Text("SELECT RISK LEVEL:", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlinkoRisk.values().forEach { r ->
                    val isSel = selectedRisk == r
                    Button(
                        onClick = {
                            selectedRisk = r
                            SoundAndHapticManager.playClick(haptic)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) GoldPrimary else CasinoSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = r.name,
                            color = if (isSel) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Bet Selector & Drop Button
            ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

            Button(
                onClick = { dropBall() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("plinko_drop_button"),
                enabled = !isDropping,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isDropping) "DROPPING..." else "DROP BALL (${formatChips(betAmount)} CHIPS)",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.PLINKO, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
