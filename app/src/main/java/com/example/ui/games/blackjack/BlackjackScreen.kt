package com.example.ui.games.blackjack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

enum class Suit(val symbol: String, val isRed: Boolean) {
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false),
    SPADES("♠", false)
}

data class PlayingCard(
    val rank: String,
    val suit: Suit,
    val value: Int,
    val isHidden: Boolean = false
)

enum class BlackjackState {
    BETTING,
    PLAYER_TURN,
    DEALER_TURN,
    ROUND_OVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlackjackScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(50L) }
    var gameState by remember { mutableStateOf(BlackjackState.BETTING) }
    var playerCards by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var dealerCards by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var splitCards by remember { mutableStateOf<List<PlayingCard>?>(null) }
    var activeHandIsSplit by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Place your bet and press DEAL.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }
    var currentRoundId by remember { mutableStateOf("") }
    var isDealing by remember { mutableStateOf(false) }

    fun calculateHandScore(cards: List<PlayingCard>): Pair<Int, Boolean> {
        val visibleCards = cards.filter { !it.isHidden }
        var total = 0
        var aces = 0
        for (card in visibleCards) {
            total += card.value
            if (card.rank == "A") aces++
        }
        var isSoft = false
        while (total > 21 && aces > 0) {
            total -= 10
            aces--
        }
        if (aces > 0 && total <= 21) isSoft = true
        return Pair(total, isSoft)
    }

    fun generateDeck(outcomeHash: String): MutableList<PlayingCard> {
        val ranks = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
        val deck = mutableListOf<PlayingCard>()
        for (suit in Suit.values()) {
            for (rank in ranks) {
                val value = when (rank) {
                    "A" -> 11
                    "K", "Q", "J", "10" -> 10
                    else -> rank.toInt()
                }
                deck.add(PlayingCard(rank, suit, value))
            }
        }
        // Shuffle deterministically with HMAC bytes
        var offset = 0
        val shuffled = mutableListOf<PlayingCard>()
        val copy = deck.toMutableList()
        while (copy.isNotEmpty()) {
            val idx = ProvablyFairEngine.hmacToInt(outcomeHash, 0, copy.size - 1, offset % 50)
            shuffled.add(copy.removeAt(idx))
            offset += 2
        }
        return shuffled
    }

    var shoe by remember { mutableStateOf<MutableList<PlayingCard>>(mutableListOf()) }

    fun dealNewGame() {
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Please claim free chips in wallet."
            return
        }

        coroutineScope.launch {
            isDealing = true
            currentRoundId = UUID.randomUUID().toString()
            lastWinAmount = 0L

            val betRes = CasinoRepository.placeBet(
                gameType = GameType.BLACKJACK,
                amount = betAmount,
                idempotencyKey = "bj_bet_$currentRoundId",
                note = "Vegas Blackjack 21 Bet: $betAmount chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                isDealing = false
                return@launch
            }

            SoundAndHapticManager.playClick(haptic)
            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            shoe = generateDeck(outcomeHash)

            playerCards = emptyList()
            dealerCards = emptyList()
            splitCards = null
            activeHandIsSplit = false

            delay(200)
            playerCards = listOf(shoe.removeAt(0))
            SoundAndHapticManager.playChipPlace(haptic)
            delay(200)
            dealerCards = listOf(shoe.removeAt(0))
            delay(200)
            playerCards = playerCards + listOf(shoe.removeAt(0))
            delay(200)
            dealerCards = dealerCards + listOf(shoe.removeAt(0).copy(isHidden = true))

            val (playerTotal, _) = calculateHandScore(playerCards)
            if (playerTotal == 21) {
                // Natural Blackjack!
                dealerCards = dealerCards.map { it.copy(isHidden = false) }
                val (dealerTotal, _) = calculateHandScore(dealerCards)
                if (dealerTotal == 21) {
                    // Push
                    statusMessage = "Both have Blackjack! PUSH (Bet Returned)."
                    CasinoRepository.creditPayout(
                        gameType = GameType.BLACKJACK,
                        amount = betAmount,
                        idempotencyKey = "bj_payout_push_$currentRoundId",
                        note = "Blackjack Push"
                    )
                } else {
                    // Payout 3:2
                    val winPayout = betAmount + (betAmount * 1.5).toLong()
                    lastWinAmount = winPayout
                    CasinoRepository.creditPayout(
                        gameType = GameType.BLACKJACK,
                        amount = winPayout,
                        idempotencyKey = "bj_payout_win_$currentRoundId",
                        note = "Blackjack 3:2 Natural Payout"
                    )
                    statusMessage = "👑 NATURAL BLACKJACK! Paid 3:2 (+${formatChips(winPayout)} chips)!"
                    SoundAndHapticManager.playBigWin(haptic)
                }
                gameState = BlackjackState.ROUND_OVER
            } else {
                gameState = BlackjackState.PLAYER_TURN
                statusMessage = "Your turn: HIT, STAND, or DOUBLE DOWN."
            }

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.BLACKJACK,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Initial: Player $playerTotal vs Dealer ${dealerCards.first().value}"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
            isDealing = false
        }
    }

    fun finishDealerTurn(finalBet: Long = betAmount) {
        coroutineScope.launch {
            gameState = BlackjackState.DEALER_TURN
            dealerCards = dealerCards.map { it.copy(isHidden = false) }
            delay(300)

            var (dealerTotal, _) = calculateHandScore(dealerCards)
            while (dealerTotal < 17 && shoe.isNotEmpty()) {
                SoundAndHapticManager.playChipPlace(haptic)
                dealerCards = dealerCards + shoe.removeAt(0)
                delay(350)
                dealerTotal = calculateHandScore(dealerCards).first
            }

            val (playerTotal, _) = calculateHandScore(playerCards)

            when {
                dealerTotal > 21 -> {
                    val win = finalBet * 2
                    lastWinAmount = win
                    CasinoRepository.creditPayout(
                        gameType = GameType.BLACKJACK,
                        amount = win,
                        idempotencyKey = "bj_dealer_bust_$currentRoundId",
                        note = "Dealer Busts"
                    )
                    statusMessage = "Dealer BUSTS! You WIN ${formatChips(win)} chips!"
                    SoundAndHapticManager.playWin(haptic)
                }
                playerTotal > dealerTotal -> {
                    val win = finalBet * 2
                    lastWinAmount = win
                    CasinoRepository.creditPayout(
                        gameType = GameType.BLACKJACK,
                        amount = win,
                        idempotencyKey = "bj_player_wins_$currentRoundId",
                        note = "Player Beats Dealer ($playerTotal vs $dealerTotal)"
                    )
                    statusMessage = "YOU WIN! ($playerTotal vs $dealerTotal) Paid ${formatChips(win)} chips!"
                    SoundAndHapticManager.playWin(haptic)
                }
                playerTotal == dealerTotal -> {
                    CasinoRepository.creditPayout(
                        gameType = GameType.BLACKJACK,
                        amount = finalBet,
                        idempotencyKey = "bj_push_$currentRoundId",
                        note = "Push tie at $playerTotal"
                    )
                    statusMessage = "PUSH at $playerTotal! Bet returned."
                }
                else -> {
                    statusMessage = "Dealer wins ($dealerTotal vs $playerTotal). Better luck next hand!"
                }
            }
            gameState = BlackjackState.ROUND_OVER
        }
    }

    fun playerHit() {
        if (shoe.isEmpty() || gameState != BlackjackState.PLAYER_TURN) return
        playerCards = playerCards + shoe.removeAt(0)
        SoundAndHapticManager.playChipPlace(haptic)
        val (score, _) = calculateHandScore(playerCards)
        if (score > 21) {
            statusMessage = "BUST! Hand total $score exceeded 21."
            dealerCards = dealerCards.map { it.copy(isHidden = false) }
            gameState = BlackjackState.ROUND_OVER
            SoundAndHapticManager.playCrash()
        } else if (score == 21) {
            finishDealerTurn()
        }
    }

    fun playerStand() {
        if (gameState != BlackjackState.PLAYER_TURN) return
        finishDealerTurn()
    }

    fun playerDoubleDown() {
        if (gameState != BlackjackState.PLAYER_TURN || playerCards.size != 2) return
        if (balance < betAmount) {
            statusMessage = "Insufficient chips to double bet."
            return
        }
        val doubleRes = CasinoRepository.placeBet(
            gameType = GameType.BLACKJACK,
            amount = betAmount,
            idempotencyKey = "bj_double_$currentRoundId",
            note = "Double Down extra bet"
        )
        if (doubleRes.isFailure) return
        SoundAndHapticManager.playChipPlace(haptic)
        playerCards = playerCards + shoe.removeAt(0)
        val (score, _) = calculateHandScore(playerCards)
        if (score > 21) {
            statusMessage = "BUST on Double Down! Hand total: $score."
            dealerCards = dealerCards.map { it.copy(isHidden = false) }
            gameState = BlackjackState.ROUND_OVER
            SoundAndHapticManager.playCrash()
        } else {
            finishDealerTurn(finalBet = betAmount * 2)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.BLACKJACK.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Dealer Stands on 17 • Blackjack Pays 3:2", fontSize = 11.sp, color = GoldLight)
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

            // Felt Table Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(20.dp)),
                color = Color(0xFF0F3820) // Authentic Casino Green Felt
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dealer Section
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val (dealerTotal, _) = calculateHandScore(dealerCards)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DEALER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                            if (dealerCards.isNotEmpty() && !dealerCards.any { it.isHidden }) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("($dealerTotal)", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dealerCards) { card ->
                                BlackjackCardView(card)
                            }
                        }
                    }

                    HorizontalDivider(color = GoldPrimary.copy(alpha = 0.3f), thickness = 1.dp)

                    // Message Banner
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Player Section
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val (playerTotal, isSoft) = calculateHandScore(playerCards)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PLAYER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                            if (playerCards.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSoft && playerTotal <= 21) "($playerTotal soft)" else "($playerTotal)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (playerTotal > 21) RubyRed else Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(playerCards) { card ->
                                BlackjackCardView(card)
                            }
                        }
                    }
                }
            }

            // Controls
            if (gameState == BlackjackState.BETTING || gameState == BlackjackState.ROUND_OVER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("SELECT BET:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { dealNewGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("bj_deal_button"),
                        enabled = !isDealing,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "DEAL (${formatChips(betAmount)} CHIPS)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            } else if (gameState == BlackjackState.PLAYER_TURN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { playerHit() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("HIT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { playerStand() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("STAND", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (playerCards.size == 2) {
                        Button(
                            onClick = { playerDoubleDown() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DOUBLE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRulesDialog) {
        InfoRulesDialog(gameType = GameType.BLACKJACK, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}

@Composable
fun BlackjackCardView(card: PlayingCard) {
    Surface(
        modifier = Modifier
            .size(width = 64.dp, height = 90.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (card.isHidden) GoldPrimary else Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
        color = if (card.isHidden) Color(0xFF1E2130) else Color(0xFFFCFCFD),
        tonalElevation = 4.dp
    ) {
        if (card.isHidden) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("👑", fontSize = 24.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = card.rank,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (card.suit.isRed) RubyRed else Color.Black
                    )
                    Text(
                        text = card.suit.symbol,
                        fontSize = 14.sp,
                        color = if (card.suit.isRed) RubyRed else Color.Black
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = card.suit.symbol,
                        fontSize = 24.sp,
                        color = if (card.suit.isRed) RubyRed else Color.Black
                    )
                }

                Text(
                    text = card.rank,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (card.suit.isRed) RubyRed else Color.Black,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
