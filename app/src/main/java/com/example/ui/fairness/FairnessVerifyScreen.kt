package com.example.ui.fairness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundAndHapticManager
import com.example.data.model.ProvablyFairRound
import com.example.data.repository.CasinoRepository
import com.example.data.repository.ProvablyFairEngine
import com.example.ui.components.PlayMoneyDisclaimerBadge
import com.example.ui.theme.*

@Composable
fun FairnessVerifyScreen(
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val history by CasinoRepository.fairnessHistory.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()

    var customServerSeed by remember { mutableStateOf("") }
    var customExpectedHash by remember { mutableStateOf("") }
    var customClientSeed by remember { mutableStateOf(userProfile.clientSeed) }
    var customNonce by remember { mutableStateOf("1") }
    var customOutcomeHash by remember { mutableStateOf("") }
    var auditResult by remember { mutableStateOf<ProvablyFairEngine.AuditResult?>(null) }

    fun populateFromRound(r: ProvablyFairRound) {
        customServerSeed = r.serverSeedSecret
        customExpectedHash = r.serverSeedHashed
        customClientSeed = r.clientSeed
        customNonce = r.nonce.toString()
        customOutcomeHash = r.outcomeHash
        auditResult = ProvablyFairEngine.verifyAudit(
            serverSeedSecret = r.serverSeedSecret,
            serverSeedHashExpected = r.serverSeedHashed,
            clientSeed = r.clientSeed,
            nonce = r.nonce,
            expectedOutcomeHash = r.outcomeHash
        )
        SoundAndHapticManager.playClick(haptic)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("PROVABLY FAIR VERIFIER", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                color = Color(0xFF131724)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cryptographic Verification Tool", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Verify that game outcomes were predetermined by HMAC-SHA256 and could not be altered mid-round by the server.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customServerSeed,
                        onValueChange = { customServerSeed = it },
                        label = { Text("Server Seed Secret (32-byte hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = CasinoCardBorder
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customExpectedHash,
                        onValueChange = { customExpectedHash = it },
                        label = { Text("Pre-committed SHA-256 Hash") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = CasinoCardBorder
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customClientSeed,
                            onValueChange = { customClientSeed = it },
                            label = { Text("Client Seed") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = CasinoCardBorder
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customNonce,
                            onValueChange = { customNonce = it },
                            label = { Text("Nonce") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = CasinoCardBorder
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customOutcomeHash,
                        onValueChange = { customOutcomeHash = it },
                        label = { Text("Expected Outcome HMAC-SHA256") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = CasinoCardBorder
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val n = customNonce.toLongOrNull() ?: 1L
                            auditResult = ProvablyFairEngine.verifyAudit(
                                serverSeedSecret = customServerSeed,
                                serverSeedHashExpected = customExpectedHash,
                                clientSeed = customClientSeed,
                                nonce = n,
                                expectedOutcomeHash = customOutcomeHash
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("COMPUTE & VERIFY PROOF", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    auditResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (res.is100PercentFair) Color(0xFF0F3318) else Color(0xFF381212),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (res.is100PercentFair) EmeraldGreen else RubyRed)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (res.is100PercentFair) "PROVABLY FAIR 100% VERIFIED!" else "VERIFICATION FAILED",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.is100PercentFair) EmeraldGreen else RubyRed
                                )
                                Text(
                                    text = "• Hash Match: ${res.isServerSeedAuthentic}\n• Outcome Match: ${res.isOutcomeAuthentic}\n• Recomputed Float: ${res.reconstructedOutcomeFloat}",
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("PREVIOUS ROUNDS LOG (${history.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
        }

        items(history) { round ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = CasinoSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(round.gameType.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Button(
                            onClick = { populateFromRound(round) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("AUDIT", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Round ID: ${round.roundId} • Nonce: ${round.nonce}", fontSize = 11.sp, color = GoldLight)
                    Text(round.outcomeSummary, fontSize = 11.sp, color = TextPrimary)
                    Text("Hash: ${round.outcomeHash.take(24)}...", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}
