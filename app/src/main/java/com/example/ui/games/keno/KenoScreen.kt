package com.example.ui.games.keno

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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KenoScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(20L) }
    var selectedNumbers by remember { mutableStateOf(setOf(7, 13, 21, 33, 44, 77)) }
    var drawnBalls by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Pick between 1 and 10 lucky numbers.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }

    fun toggleNumber(num: Int) {
        if (isDrawing) return
        val current = selectedNumbers.toMutableSet()
        if (current.contains(num)) {
            current.remove(num)
        } else {
            if (current.size < 10) {
                current.add(num)
            } else {
                statusMessage = "Maximum 10 numbers allowed!"
                return
            }
        }
        selectedNumbers = current
        SoundAndHapticManager.playClick(haptic)
    }

    fun quickPick(count: Int = 6) {
        if (isDrawing) return
        val pool = (1..80).shuffled()
        selectedNumbers = pool.take(count).toSet()
        SoundAndHapticManager.playChipPlace(haptic)
    }

    fun clearPicks() {
        if (isDrawing) return
        selectedNumbers = emptySet()
        SoundAndHapticManager.playClick(haptic)
    }

    fun calculateKenoMultiplier(pickedCount: Int, hits: Int): Int {
        if (hits == 0) return 0
        return when (pickedCount) {
            1 -> if (hits == 1) 3 else 0
            2 -> if (hits == 2) 9 else 0
            3 -> when (hits) { 2 -> 2; 3 -> 25; else -> 0 }
            4 -> when (hits) { 2 -> 1; 3 -> 5; 4 -> 50; else -> 0 }
            5 -> when (hits) { 3 -> 3; 4 -> 15; 5 -> 150; else -> 0 }
            6 -> when (hits) { 3 -> 2; 4 -> 8; 5 -> 40; 6 -> 500; else -> 0 }
            7 -> when (hits) { 4 -> 5; 5 -> 20; 6 -> 100; 7 -> 1500; else -> 0 }
            8 -> when (hits) { 4 -> 3; 5 -> 10; 6 -> 50; 7 -> 400; 8 -> 3000; else -> 0 }
            9 -> when (hits) { 5 -> 8; 6 -> 30; 7 -> 150; 8 -> 1000; 9 -> 5000; else -> 0 }
            10 -> when (hits) { 5 -> 5; 6 -> 20; 7 -> 80; 8 -> 500; 9 -> 2500; 10 -> 10000; else -> 0 }
            else -> 0
        }
    }

    fun draw20Balls() {
        if (isDrawing || selectedNumbers.isEmpty()) return
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            isDrawing = true
            drawnBalls = emptyList()
            lastWinAmount = 0L
            statusMessage = "Drawing 20 balls from the Keno hopper..."

            val roundId = UUID.randomUUID().toString()
            val betRes = CasinoRepository.placeBet(
                gameType = GameType.KENO,
                amount = betAmount,
                idempotencyKey = "keno_bet_$roundId",
                note = "Keno (${selectedNumbers.size} picks): $betAmount chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isDrawing = false
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            val pool = (1..80).toMutableList()
            val finalDrawn = mutableListOf<Int>()
            for (i in 0 until 20) {
                val idx = ProvablyFairEngine.hmacToInt(outcomeHash, 0, pool.size - 1, (i * 2) % 40)
                finalDrawn.add(pool.removeAt(idx))
            }

            for (ball in finalDrawn) {
                delay(70)
                drawnBalls = drawnBalls + ball
                if (selectedNumbers.contains(ball)) {
                    SoundAndHapticManager.playWin(haptic)
                } else {
                    SoundAndHapticManager.playChipPlace(haptic)
                }
            }

            val hits = drawnBalls.count { selectedNumbers.contains(it) }
            val mult = calculateKenoMultiplier(selectedNumbers.size, hits)
            val payout = (betAmount * mult).toLong()
            lastWinAmount = payout

            if (payout > 0) {
                CasinoRepository.creditPayout(
                    gameType = GameType.KENO,
                    amount = payout,
                    idempotencyKey = "keno_payout_$roundId",
                    note = "Keno $hits Hits Win"
                )
                SoundAndHapticManager.playBigWin(haptic)
                statusMessage = "🎉 $hits HITS OUT OF ${selectedNumbers.size}! Paid ${formatChips(payout)} chips!"
            } else {
                statusMessage = "$hits hits out of ${selectedNumbers.size}. No payout. Better luck next draw!"
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.KENO,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Picks: ${selectedNumbers.size} | Hits: $hits | Payout: $payout chips"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isDrawing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.KENO.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Pick 1-10 • Draw 20 Balls • Up to 10,000x", fontSize = 11.sp, color = GoldLight)
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

            // Drawn Balls Stream (Last 20)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("DRAWN BALLS (${drawnBalls.size}/20):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(drawnBalls) { b ->
                        val isHit = selectedNumbers.contains(b)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isHit) GoldPrimary else Color(0xFF2B2F44)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$b",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHit) Color.Black else Color.White
                            )
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

            // 80 Numbers Grid (8 rows of 10 columns)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131522), RoundedCornerShape(12.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (r in 0..7) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (c in 1..10) {
                            val num = (r * 10) + c
                            val isPicked = selectedNumbers.contains(num)
                            val isDrawn = drawnBalls.contains(num)
                            val isHit = isPicked && isDrawn

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            isHit -> EmeraldGreen
                                            isPicked -> GoldPrimary
                                            isDrawn -> Color(0xFF282D42)
                                            else -> Color(0xFF1B1E2E)
                                        }
                                    )
                                    .border(
                                        width = if (isHit || isPicked) 1.5.dp else 0.5.dp,
                                        color = if (isHit) EmeraldGreen else if (isPicked) GoldLight else Color(0xFF2C324A),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { toggleNumber(num) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$num",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHit || isPicked) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Quick Pick & Clear Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { quickPick(6) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    enabled = !isDrawing
                ) {
                    Text("QUICK PICK 6", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { quickPick(10) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    enabled = !isDrawing
                ) {
                    Text("QUICK 10", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { clearPicks() },
                    modifier = Modifier.weight(1f).height(42.dp),
                    enabled = !isDrawing && selectedNumbers.isNotEmpty()
                ) {
                    Text("CLEAR", fontSize = 11.sp, color = RubyRed, fontWeight = FontWeight.Bold)
                }
            }

            // Bet Selector & Draw 20 Button
            ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

            Button(
                onClick = { draw20Balls() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("keno_draw_button"),
                enabled = !isDrawing && selectedNumbers.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isDrawing) "DRAWING 20 BALLS..." else "DRAW 20 BALLS (${formatChips(betAmount)} CHIPS)",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.KENO, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
