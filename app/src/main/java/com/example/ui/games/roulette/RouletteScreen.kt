package com.example.ui.games.roulette

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
import androidx.compose.material.icons.filled.Refresh
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

val RED_NUMBERS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

enum class BetType(val label: String, val multiplier: Int) {
    RED("RED", 2),
    BLACK("BLACK", 2),
    EVEN("EVEN", 2),
    ODD("ODD", 2),
    LOW_1_18("1-18", 2),
    HIGH_19_36("19-36", 2),
    DOZEN_1("1st 12", 3),
    DOZEN_2("2nd 12", 3),
    DOZEN_3("3rd 12", 3)
}

data class ActiveBet(
    val betType: BetType? = null,
    val straightNumber: Int? = null,
    val amount: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouletteScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var chipSelected by remember { mutableLongStateOf(25L) }
    var bets by remember { mutableStateOf<List<ActiveBet>>(emptyList()) }
    var isSpinning by remember { mutableStateOf(false) }
    var landedNumber by remember { mutableStateOf<Int?>(null) }
    var recentHistory by remember { mutableStateOf(listOf(7, 24, 0, 18, 32, 15)) }
    var statusMessage by remember { mutableStateOf("Place bets on Red, Black, Dozens, or Numbers.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }

    val totalBetsPlaced = bets.sumOf { it.amount }

    fun addBet(type: BetType? = null, straight: Int? = null) {
        if (isSpinning) return
        val currentBetForThis = bets.find { it.betType == type && it.straightNumber == straight }?.amount ?: 0L
        val updatedBets = bets.filterNot { it.betType == type && it.straightNumber == straight } +
                ActiveBet(type, straight, currentBetForThis + chipSelected)
        bets = updatedBets
        SoundAndHapticManager.playChipPlace(haptic)
    }

    fun clearBets() {
        if (isSpinning) return
        bets = emptyList()
        SoundAndHapticManager.playClick(haptic)
    }

    fun spinWheel() {
        if (isSpinning || bets.isEmpty()) return
        if (balance < totalBetsPlaced) {
            statusMessage = "Insufficient chips! Add chips or reduce bets."
            return
        }

        coroutineScope.launch {
            isSpinning = true
            statusMessage = "Ball is spinning on the wheel..."
            lastWinAmount = 0L

            val roundId = UUID.randomUUID().toString()
            val betRes = CasinoRepository.placeBet(
                gameType = GameType.ROULETTE,
                amount = totalBetsPlaced,
                idempotencyKey = "roulette_bet_$roundId",
                note = "Roulette: ${bets.size} bets total $totalBetsPlaced chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isSpinning = false
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            // Provably fair outcome
            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")
            val winningNumber = ProvablyFairEngine.hmacToInt(outcomeHash, 0, 36)

            // Wheel spin delay
            for (step in 1..20) {
                landedNumber = (0..36).random()
                delay(70)
            }
            landedNumber = winningNumber
            recentHistory = (listOf(winningNumber) + recentHistory).take(10)

            // Evaluate Payouts
            var totalPayout = 0L
            val isRed = RED_NUMBERS.contains(winningNumber)
            val isEven = winningNumber != 0 && winningNumber % 2 == 0
            val isLow = winningNumber in 1..18
            val isHigh = winningNumber in 19..36

            for (bet in bets) {
                if (bet.straightNumber != null && bet.straightNumber == winningNumber) {
                    totalPayout += bet.amount * 36 // 35:1 + original
                }
                bet.betType?.let { t ->
                    val won = when (t) {
                        BetType.RED -> isRed
                        BetType.BLACK -> winningNumber != 0 && !isRed
                        BetType.EVEN -> isEven
                        BetType.ODD -> winningNumber != 0 && !isEven
                        BetType.LOW_1_18 -> isLow
                        BetType.HIGH_19_36 -> isHigh
                        BetType.DOZEN_1 -> winningNumber in 1..12
                        BetType.DOZEN_2 -> winningNumber in 13..24
                        BetType.DOZEN_3 -> winningNumber in 25..36
                    }
                    if (won) totalPayout += bet.amount * t.multiplier
                }
            }

            val winColor = if (winningNumber == 0) "GREEN 0" else if (isRed) "RED $winningNumber" else "BLACK $winningNumber"
            if (totalPayout > 0) {
                lastWinAmount = totalPayout
                CasinoRepository.creditPayout(
                    gameType = GameType.ROULETTE,
                    amount = totalPayout,
                    idempotencyKey = "roulette_payout_$roundId",
                    note = "Roulette win on $winColor"
                )
                SoundAndHapticManager.playWin(haptic)
                statusMessage = "🎉 BALL LANDED ON $winColor! YOU WON ${formatChips(totalPayout)} CHIPS!"
            } else {
                statusMessage = "Ball landed on $winColor. No winning bets this round."
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.ROULETTE,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Landed: $winColor | Total Bet: $totalBetsPlaced | Payout: $totalPayout"
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
                        Text(GameType.ROULETTE.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("European Single Zero • 35:1 Straight Up", fontSize = 11.sp, color = GoldLight)
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

            // Wheel / Number Spotlight and History
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Brush.horizontalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(16.dp)),
                color = Color(0xFF141724)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Winning Number Spotlight
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WINNING NUMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        landedNumber == null -> Color(0xFF262A3D)
                                        landedNumber == 0 -> EmeraldGreen
                                        RED_NUMBERS.contains(landedNumber) -> RubyRed
                                        else -> Color.Black
                                    }
                                )
                                .border(2.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = landedNumber?.toString() ?: "•",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // History Board
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("RECENT HISTORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(recentHistory) { num ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (num == 0) EmeraldGreen else if (RED_NUMBERS.contains(num)) RubyRed else Color.Black
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "$num", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Status Banner
            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (lastWinAmount > 0) EmeraldGreen else Color.White
            )

            // Outside Betting Board (Red, Black, Even, Odd, Low, High)
            Text("OUTSIDE BETS (EVEN MONEY & DOZENS):", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RouletteBetButton(
                    label = "RED",
                    color = RubyRed,
                    currentBet = bets.find { it.betType == BetType.RED }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.RED) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "BLACK",
                    color = Color(0xFF1E212B),
                    currentBet = bets.find { it.betType == BetType.BLACK }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.BLACK) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "EVEN",
                    color = Color(0xFF263238),
                    currentBet = bets.find { it.betType == BetType.EVEN }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.EVEN) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "ODD",
                    color = Color(0xFF263238),
                    currentBet = bets.find { it.betType == BetType.ODD }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.ODD) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RouletteBetButton(
                    label = "1-18",
                    color = Color(0xFF2A2D3D),
                    currentBet = bets.find { it.betType == BetType.LOW_1_18 }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.LOW_1_18) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "1st 12",
                    color = Color(0xFF2A2D3D),
                    currentBet = bets.find { it.betType == BetType.DOZEN_1 }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.DOZEN_1) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "2nd 12",
                    color = Color(0xFF2A2D3D),
                    currentBet = bets.find { it.betType == BetType.DOZEN_2 }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.DOZEN_2) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "3rd 12",
                    color = Color(0xFF2A2D3D),
                    currentBet = bets.find { it.betType == BetType.DOZEN_3 }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.DOZEN_3) },
                    modifier = Modifier.weight(1f)
                )
                RouletteBetButton(
                    label = "19-36",
                    color = Color(0xFF2A2D3D),
                    currentBet = bets.find { it.betType == BetType.HIGH_19_36 }?.amount ?: 0L,
                    onClick = { addBet(type = BetType.HIGH_19_36) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Single Numbers Grid (0..36)
            Text("STRAIGHT UP NUMBERS (35:1 PAYOUT):", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F2617), RoundedCornerShape(12.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 0 Green
                Button(
                    onClick = { addBet(straight = 0) },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    val b0 = bets.find { it.straightNumber == 0 }?.amount ?: 0L
                    Text("0 (ZERO) ${if (b0 > 0) "[$b0]" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Grid of 3 rows x 12 cols
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0..11) {
                            val num = (col * 3) + (3 - row)
                            val isR = RED_NUMBERS.contains(num)
                            val betOnNum = bets.find { it.straightNumber == num }?.amount ?: 0L
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isR) RubyRed else Color.Black)
                                    .border(
                                        width = if (betOnNum > 0) 2.dp else 0.5.dp,
                                        color = if (betOnNum > 0) GoldPrimary else Color.Gray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { addBet(straight = num) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (betOnNum > 0) "$num*" else "$num",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Chip Value Selector & Actions
            ChipSelectorBar(currentBet = chipSelected, onBetSelected = { chipSelected = it })

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { clearBets() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = !isSpinning && bets.isNotEmpty()
                ) {
                    Text("CLEAR BETS", color = RubyRed, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { spinWheel() },
                    modifier = Modifier.weight(2f).height(50.dp).testTag("roulette_spin_button"),
                    enabled = !isSpinning && bets.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(
                        text = if (isSpinning) "SPINNING..." else "SPIN (${formatChips(totalBetsPlaced)} CHIPS)",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.ROULETTE, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}

@Composable
fun RouletteBetButton(
    label: String,
    color: Color,
    currentBet: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (currentBet > 0) 2.dp else 1.dp,
                color = if (currentBet > 0) GoldPrimary else Color(0xFF3E4357),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        color = color
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (currentBet > 0) {
                Text("${currentBet}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GoldLight)
            }
        }
    }
}
