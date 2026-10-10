package com.example.ui.games.videopoker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.model.GameType
import com.example.data.model.ProvablyFairRound
import com.example.data.repository.CasinoRepository
import com.example.data.repository.ProvablyFairEngine
import com.example.ui.components.*
import com.example.ui.games.blackjack.PlayingCard
import com.example.ui.games.blackjack.Suit
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class PokerHandRank(val label: String, val multiplier: Int) {
    ROYAL_FLUSH("Royal Flush", 800),
    STRAIGHT_FLUSH("Straight Flush", 50),
    FOUR_OF_A_KIND("Four of a Kind", 25),
    FULL_HOUSE("Full House", 9),
    FLUSH("Flush", 6),
    STRAIGHT("Straight", 4),
    THREE_OF_A_KIND("Three of a Kind", 3),
    TWO_PAIR("Two Pair", 2),
    JACKS_OR_BETTER("Jacks or Better", 1),
    NOTHING("No Hand", 0)
}

enum class PokerStage {
    BETTING,
    HOLDING,
    RESULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPokerScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val balance by CasinoRepository.balance.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var betAmount by remember { mutableLongStateOf(25L) }
    var stage by remember { mutableStateOf(PokerStage.BETTING) }
    var hand by remember { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var heldIndices by remember { mutableStateOf(setOf<Int>()) }
    var evaluatedRank by remember { mutableStateOf(PokerHandRank.NOTHING) }
    var statusMessage by remember { mutableStateOf("Place bet and press DEAL.") }
    var lastWinAmount by remember { mutableLongStateOf(0L) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showFairnessDialog by remember { mutableStateOf(false) }
    var lastRound by remember { mutableStateOf<ProvablyFairRound?>(null) }
    var deckRemaining by remember { mutableStateOf<MutableList<PlayingCard>>(mutableListOf()) }
    var activeRoundId by remember { mutableStateOf("") }

    fun rankValue(r: String): Int = when (r) {
        "A" -> 14
        "K" -> 13
        "Q" -> 12
        "J" -> 11
        "10" -> 10
        else -> r.toInt()
    }

    fun evaluatePokerHand(cards: List<PlayingCard>): PokerHandRank {
        if (cards.size < 5) return PokerHandRank.NOTHING
        val ranks = cards.map { rankValue(it.rank) }.sorted()
        val suits = cards.map { it.suit }

        val isFlush = suits.distinct().size == 1
        val isStraight = ranks == listOf(ranks[0], ranks[0] + 1, ranks[0] + 2, ranks[0] + 3, ranks[0] + 4) ||
                ranks == listOf(2, 3, 4, 5, 14) // Ace-low straight

        val rankCounts = ranks.groupingBy { it }.eachCount().values.sortedDescending()

        return when {
            isFlush && ranks == listOf(10, 11, 12, 13, 14) -> PokerHandRank.ROYAL_FLUSH
            isFlush && isStraight -> PokerHandRank.STRAIGHT_FLUSH
            rankCounts == listOf(4, 1) -> PokerHandRank.FOUR_OF_A_KIND
            rankCounts == listOf(3, 2) -> PokerHandRank.FULL_HOUSE
            isFlush -> PokerHandRank.FLUSH
            isStraight -> PokerHandRank.STRAIGHT
            rankCounts == listOf(3, 1, 1) -> PokerHandRank.THREE_OF_A_KIND
            rankCounts == listOf(2, 2, 1) -> PokerHandRank.TWO_PAIR
            rankCounts.firstOrNull() == 2 -> {
                // Check if pair is Jacks or Better (J, Q, K, A)
                val pairRank = ranks.groupingBy { it }.eachCount().filter { it.value == 2 }.keys.firstOrNull()
                if (pairRank != null && pairRank >= 11) PokerHandRank.JACKS_OR_BETTER else PokerHandRank.NOTHING
            }
            else -> PokerHandRank.NOTHING
        }
    }

    fun dealFirstHand() {
        if (balance < betAmount) {
            statusMessage = "Insufficient chips! Add chips in wallet."
            return
        }

        coroutineScope.launch {
            activeRoundId = UUID.randomUUID().toString()
            lastWinAmount = 0L
            heldIndices = emptySet()

            val betRes = CasinoRepository.placeBet(
                gameType = GameType.VIDEO_POKER,
                amount = betAmount,
                idempotencyKey = "poker_bet_$activeRoundId",
                note = "Jacks or Better: $betAmount chips"
            )
            if (betRes.isFailure) {
                statusMessage = betRes.exceptionOrNull()?.message ?: "Bet error"
                return@launch
            }

            SoundAndHapticManager.playSpin(haptic)

            val serverSeed = ProvablyFairEngine.generateServerSeed()
            val nonce = CasinoRepository.getNextNonce()
            val outcomeHash = ProvablyFairEngine.hmacSha256(serverSeed, "${userProfile.clientSeed}:$nonce")

            val fullDeck = mutableListOf<PlayingCard>()
            val rList = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
            for (s in Suit.values()) {
                for (r in rList) {
                    fullDeck.add(PlayingCard(r, s, rankValue(r)))
                }
            }
            fullDeck.shuffle()

            hand = emptyList()
            for (i in 0..4) {
                delay(120)
                hand = hand + fullDeck.removeAt(0)
                SoundAndHapticManager.playChipPlace(haptic)
            }
            deckRemaining = fullDeck

            evaluatedRank = evaluatePokerHand(hand)
            stage = PokerStage.HOLDING
            statusMessage = "Tap cards to HOLD, then press DRAW."

            val roundRecord = ProvablyFairEngine.createRoundRecord(
                gameType = GameType.VIDEO_POKER,
                serverSeed = serverSeed,
                clientSeed = userProfile.clientSeed,
                nonce = nonce,
                summary = "Initial hand dealt: ${hand.map { it.rank + it.suit.symbol }}"
            )
            lastRound = roundRecord
            CasinoRepository.recordProvablyFairRound(roundRecord)
        }
    }

    fun drawSecondHand() {
        if (stage != PokerStage.HOLDING) return
        coroutineScope.launch {
            val newHand = hand.toMutableList()
            for (i in 0..4) {
                if (!heldIndices.contains(i)) {
                    delay(120)
                    newHand[i] = deckRemaining.removeAt(0)
                    SoundAndHapticManager.playChipPlace(haptic)
                }
            }
            hand = newHand
            evaluatedRank = evaluatePokerHand(hand)
            stage = PokerStage.RESULT

            if (evaluatedRank.multiplier > 0) {
                val win = betAmount * (evaluatedRank.multiplier + 1L)
                lastWinAmount = win
                CasinoRepository.creditPayout(
                    gameType = GameType.VIDEO_POKER,
                    amount = win,
                    idempotencyKey = "poker_payout_$activeRoundId",
                    note = "Video Poker ${evaluatedRank.label} Win"
                )
                SoundAndHapticManager.playWin(haptic)
                statusMessage = "🎉 ${evaluatedRank.label}! Won ${formatChips(win)} chips!"
            } else {
                statusMessage = "No winning combination. Try another hand!"
            }
        }
    }

    fun toggleHold(index: Int) {
        if (stage != PokerStage.HOLDING) return
        val updated = heldIndices.toMutableSet()
        if (updated.contains(index)) updated.remove(index) else updated.add(index)
        heldIndices = updated
        SoundAndHapticManager.playClick(haptic)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(GameType.VIDEO_POKER.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Jacks or Better 9/6 • Royal Flush 800:1", fontSize = 11.sp, color = GoldLight)
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

            // Paytable Mini-Summary Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("PAYTABLE MULTIPLIERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Royal Flush (800x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.ROYAL_FLUSH) EmeraldGreen else TextSecondary)
                            Text("Straight Flush (50x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.STRAIGHT_FLUSH) EmeraldGreen else TextSecondary)
                            Text("4 of a Kind (25x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.FOUR_OF_A_KIND) EmeraldGreen else TextSecondary)
                        }
                        Column {
                            Text("Full House (9x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.FULL_HOUSE) EmeraldGreen else TextSecondary)
                            Text("Flush (6x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.FLUSH) EmeraldGreen else TextSecondary)
                            Text("Straight (4x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.STRAIGHT) EmeraldGreen else TextSecondary)
                        }
                        Column {
                            Text("3 of a Kind (3x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.THREE_OF_A_KIND) EmeraldGreen else TextSecondary)
                            Text("Two Pair (2x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.TWO_PAIR) EmeraldGreen else TextSecondary)
                            Text("Jacks+ (1x)", fontSize = 10.sp, color = if (evaluatedRank == PokerHandRank.JACKS_OR_BETTER) EmeraldGreen else TextSecondary)
                        }
                    }
                }
            }

            // Cards Table
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Brush.verticalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(16.dp)),
                color = Color(0xFF0F2618) // Rich Velvet Green
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0..4) {
                            val card = hand.getOrNull(i)
                            val isHeld = heldIndices.contains(i)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (stage == PokerStage.HOLDING) {
                                    Text(
                                        text = if (isHeld) "HELD" else "TAP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isHeld) EmeraldGreen else GoldLight
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Surface(
                                    modifier = Modifier
                                        .size(width = 58.dp, height = 86.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isHeld) 2.5.dp else 1.dp,
                                            color = if (isHeld) EmeraldGreen else Color.White.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { toggleHold(i) },
                                    color = Color(0xFFFAFAFA)
                                ) {
                                    if (card != null) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${card.rank}${card.suit.symbol}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (card.suit.isRed) RubyRed else Color.Black
                                            )
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = card.suit.symbol,
                                                    fontSize = 22.sp,
                                                    color = if (card.suit.isRed) RubyRed else Color.Black
                                                )
                                            }
                                            Text(
                                                text = card.rank,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (card.suit.isRed) RubyRed else Color.Black,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("🂠", fontSize = 28.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Controls
            if (stage == PokerStage.HOLDING) {
                Button(
                    onClick = { drawSecondHand() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("poker_draw_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("DRAW REPLACEMENTS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            } else {
                ChipSelectorBar(currentBet = betAmount, onBetSelected = { betAmount = it })

                Button(
                    onClick = { dealFirstHand() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("poker_deal_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "DEAL HAND (${formatChips(betAmount)} CHIPS)",
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
        InfoRulesDialog(gameType = GameType.VIDEO_POKER, onDismiss = { showRulesDialog = false })
    }

    if (showFairnessDialog) {
        VerifyFairnessDialog(round = lastRound, onDismiss = { showFairnessDialog = false })
    }
}
