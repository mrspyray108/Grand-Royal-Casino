package com.example.ui.games.baccarat

import androidx.compose.animation.AnimatedVisibility
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
import com.example.ui.games.blackjack.BlackjackCardView
import com.example.ui.games.blackjack.PlayingCard
import com.example.ui.games.blackjack.Suit
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class BaccaratBetSide(val label: String, val multiplier: Double, val color: Color) {
    PLAYER("PLAYER (1:1)", 2.0, SapphireBlue),
    BANKER("BANKER (0.95:1)", 1.95, RubyRed),
    TIE("TIE (8:1)", 9.0, EmeraldGreen)
}

enum class BaccaratWinner {
    PLAYER, BANKER, TIE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaccaratScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var selectedSide by remember { mutableStateOf(BaccaratBetSide.PLAYER) }
    var betAmount by remember { mutableLongStateOf(50L) }
    var isDealing by remember { mutableStateOf(false) }

    var playerCards by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var bankerCards by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var beadPlateHistory by remember { mutableStateOf(listOf(BaccaratWinner.BANKER, BaccaratWinner.PLAYER, BaccaratWinner.BANKER, BaccaratWinner.TIE)) }
    var statusMessage by remember { mutableStateOf("Choose side (Player, Banker, or Tie) and DEAL.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }

    fun baccaratScore(cards: List<PlayingCard>): Int {
        var total = 0
        for (c in cards) {
            val v = when (c.rank) {
                "A" -> 1
                "K", "Q", "J", "10" -> 0
                else -> c.rank.toInt()
            }
            total += v
        }
        return total % 10
    }

    fun dealRound() {
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            isDealing = true
            statusMessage = "Dealing Baccarat hand..."
            lastWinAmount = 0L

            val roundId = UUID.randomUUID().toString()
            val betRes = CasinoRepository.placeBet(
                gameType = GameType.BACCARAT,
                amount = betAmount,
                idempotencyKey = "baccarat_bet_$roundId",
                note = "Baccarat ${selectedSide.label} bet: $betAmount chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isDealing = false
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            val deck = mutableListOf<PlayingCard>()
            val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
            for (suit in Suit.values()) {
                for (rank in ranks) {
                    val value = when (rank) {
                        "A" -> 1
                        "K", "Q", "J", "10" -> 0
                        else -> rank.toInt()
                    }
                    deck.add(PlayingCard(rank, suit, value))
                }
            }
            deck.shuffle()

            playerCards = emptyList()
            bankerCards = emptyList()

            // 1st card Player, 1st card Banker, 2nd card Player, 2nd card Banker
            delay(200)
            playerCards = listOf(deck.removeAt(0))
            SoundAndHapticManager.playChipPlace(haptic)
            delay(200)
            bankerCards = listOf(deck.removeAt(0))
            delay(200)
            playerCards = playerCards + listOf(deck.removeAt(0))
            delay(200)
            bankerCards = bankerCards + listOf(deck.removeAt(0))

            var pScore = baccaratScore(playerCards)
            var bScore = baccaratScore(bankerCards)

            // Check Natural (8 or 9)
            if (pScore >= 8 || bScore >= 8) {
                statusMessage = "Natural Hand! (P: $pScore vs B: $bScore)"
            } else {
                // Player third card rule: Player draws if 0-5, stands on 6-7
                var playerThirdCard: PlayingCard? = null
                if (pScore <= 5) {
                    delay(300)
                    playerThirdCard = deck.removeAt(0)
                    playerCards = playerCards + playerThirdCard
                    pScore = baccaratScore(playerCards)
                    SoundAndHapticManager.playChipPlace(haptic)
                }

                // Banker third card tableau
                if (playerThirdCard == null) {
                    // Player stood (had 6 or 7): Banker draws if 0-5, stands on 6-7
                    if (bScore <= 5) {
                        delay(300)
                        bankerCards = bankerCards + deck.removeAt(0)
                        bScore = baccaratScore(bankerCards)
                        SoundAndHapticManager.playChipPlace(haptic)
                    }
                } else {
                    // Player drew a 3rd card
                    val p3Val = playerThirdCard.value
                    val bankerDraws = when (bScore) {
                        0, 1, 2 -> true
                        3 -> p3Val != 8
                        4 -> p3Val in 2..7
                        5 -> p3Val in 4..7
                        6 -> p3Val in 6..7
                        else -> false // 7 stands
                    }
                    if (bankerDraws) {
                        delay(300)
                        bankerCards = bankerCards + deck.removeAt(0)
                        bScore = baccaratScore(bankerCards)
                        SoundAndHapticManager.playChipPlace(haptic)
                    }
                }
            }

            // Determine winner
            val winner = when {
                pScore > bScore -> BaccaratWinner.PLAYER
                bScore > pScore -> BaccaratWinner.BANKER
                else -> BaccaratWinner.TIE
            }
            beadPlateHistory = (listOf(winner) + beadPlateHistory).take(12)

            val won = (winner == BaccaratWinner.PLAYER && selectedSide == BaccaratBetSide.PLAYER) ||
                    (winner == BaccaratWinner.BANKER && selectedSide == BaccaratBetSide.BANKER) ||
                    (winner == BaccaratWinner.TIE && selectedSide == BaccaratBetSide.TIE)

            if (won) {
                val payout = (betAmount * selectedSide.multiplier).toLong()
                lastWinAmount = payout
                CasinoRepository.creditPayout(
                    gameType = GameType.BACCARAT,
                    amount = payout,
                    idempotencyKey = "baccarat_payout_$roundId",
                    note = "Baccarat ${winner.name} Win"
                )
                SoundAndHapticManager.playWin(haptic)
                statusMessage = "🎉 ${winner.name} WINS ($pScore to $bScore)! Paid ${formatChips(payout)} chips!"
            } else if (winner == BaccaratWinner.TIE && selectedSide != BaccaratBetSide.TIE) {
                // Tie pushes player/banker bets
                CasinoRepository.creditPayout(
                    gameType = GameType.BACCARAT,
                    amount = betAmount,
                    idempotencyKey = "baccarat_push_$roundId",
                    note = "Baccarat Tie Push"
                )
                statusMessage = "🤝 TIE ($pScore to $bScore)! Bet returned (Push)."
            } else {
                statusMessage = "${winner.name} wins ($pScore to $bScore). House takes hand."
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.BACCARAT,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "P: $pScore | B: $bScore | Winner: ${winner.name}"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isDealing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.BACCARAT.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Punto Banco • Standard 3rd Card Tableau", fontSize = 11.sp, color = GoldLight)
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

            // Baccarat Table Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF0F1E38) // Deep Blue Royale Felt
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bead Plate History
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BEAD PLATE ROAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(beadPlateHistory) { w ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (w) {
                                                BaccaratWinner.PLAYER -> SapphireBlue
                                                BaccaratWinner.BANKER -> RubyRed
                                                BaccaratWinner.TIE -> EmeraldGreen
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (w) {
                                            BaccaratWinner.PLAYER -> "P"
                                            BaccaratWinner.BANKER -> "B"
                                            BaccaratWinner.TIE -> "T"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = GoldPrimary.copy(alpha = 0.3f), thickness = 1.dp)

                    // Player & Banker Hands
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Player Side
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PLAYER", fontSize = 13.sp, fontWeight = FontWeight.Black, color = SapphireBlue)
                            Text(
                                text = "Score: ${baccaratScore(playerCards)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(playerCards) { card ->
                                    BlackjackCardView(card)
                                }
                            }
                        }

                        // Banker Side
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("BANKER", fontSize = 13.sp, fontWeight = FontWeight.Black, color = RubyRed)
                            Text(
                                text = "Score: ${baccaratScore(bankerCards)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(bankerCards) { card ->
                                    BlackjackCardView(card)
                                }
                            }
                        }
                    }

                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Bet Side Selection
            Text("SELECT BET POSITION:", fontSize = 11.sp, color = GoldLight, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BaccaratBetSide.values().forEach { side ->
                    val isSel = selectedSide == side
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) GoldPrimary else Color(0xFF2A2E44),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedSide = side
                                SoundAndHapticManager.playClick(haptic)
                            },
                        color = if (isSel) side.color.copy(alpha = 0.35f) else CasinoSurfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(side.name, fontSize = 13.sp, fontWeight = FontWeight.Black, color = side.color)
                            Text(
                                text = if (side == BaccaratBetSide.BANKER) "Pays 0.95:1" else if (side == BaccaratBetSide.TIE) "Pays 8:1" else "Pays 1:1",
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Chip Selector & Deal
            ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

            Button(
                onClick = { dealRound() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("baccarat_deal_button"),
                enabled = !isDealing,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isDealing) "DEALING..." else "DEAL ${selectedSide.name} (${formatChips(betAmount)} CHIPS)",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.BACCARAT, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
