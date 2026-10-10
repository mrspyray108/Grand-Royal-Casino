package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.audio.SoundAndHapticManager
import com.example.data.model.VipTier
import com.example.data.repository.CasinoRepository
import com.example.ui.components.PlayMoneyDisclaimerBadge
import com.example.ui.components.formatChips
import com.example.ui.login.AuthUiState
import com.example.ui.login.LoginViewModel
import com.example.ui.theme.*

val AVATAR_OPTIONS = listOf("👑", "🦁", "💎", "🎩", "⭐", "🐉", "🎲", "🚀")

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenResponsibleGaming: () -> Unit,
    loginViewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val authState by loginViewModel.uiState.collectAsState()
    val userProfile by CasinoRepository.userProfile.collectAsState()
    val isSoundOn by CasinoRepository.isSoundEnabled.collectAsState()
    val isHapticsOn by CasinoRepository.isHapticsEnabled.collectAsState()

    var usernameInput by remember { mutableStateOf(userProfile.username) }
    var clientSeedInput by remember { mutableStateOf(userProfile.clientSeed) }
    var selectedAvatar by remember { mutableStateOf(userProfile.avatarEmoji) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val nextTier = when (userProfile.vipTier) {
        VipTier.BRONZE -> VipTier.SILVER
        VipTier.SILVER -> VipTier.GOLD
        VipTier.GOLD -> VipTier.PLATINUM
        VipTier.PLATINUM -> VipTier.DIAMOND
        VipTier.DIAMOND -> null
    }

    val progressToNextTier = if (nextTier != null) {
        val current = userProfile.totalBets.toFloat()
        val target = nextTier.threshold.toFloat()
        (current / target).coerceIn(0f, 1f)
    } else 1.0f

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
                Text("ROYAL PLAYER PROFILE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        item {
            PlayMoneyDisclaimerBadge()
        }

        // Profile Avatar & Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, Brush.horizontalGradient(listOf(GoldPrimary, GoldDark)), RoundedCornerShape(18.dp)),
                color = Color(0xFF141726)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GoldPrimary, GoldDark)))
                            .border(3.dp, GoldLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedAvatar, fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(userProfile.username, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("${userProfile.vipTier.title} VIP MEMBER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Choose avatar
                    Text("CHOOSE AVATAR:", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(AVATAR_OPTIONS) { av ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedAvatar == av) GoldPrimary else CasinoSurfaceVariant)
                                    .clickable {
                                        selectedAvatar = av
                                        CasinoRepository.updateProfile(usernameInput, av)
                                        SoundAndHapticManager.playClick(haptic)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(av, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }

        // Firebase Auth & Google Sign-In Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FIREBASE AUTHENTICATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            color = when (authState) {
                                is AuthUiState.Success -> EmeraldGreen.copy(alpha = 0.2f)
                                is AuthUiState.Loading -> GoldPrimary.copy(alpha = 0.2f)
                                else -> TextSecondary.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = when (authState) {
                                    is AuthUiState.Success -> "SIGNED IN"
                                    is AuthUiState.Loading -> "CONNECTING"
                                    is AuthUiState.Error -> "ERROR"
                                    else -> "GUEST MODE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (authState) {
                                    is AuthUiState.Success -> EmeraldGreen
                                    is AuthUiState.Loading -> GoldPrimary
                                    is AuthUiState.Error -> RubyRed
                                    else -> TextSecondary
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = authState) {
                        is AuthUiState.Success -> {
                            val user = state.user
                            Text(
                                text = "Connected: ${user.displayName ?: user.email ?: "Google Account"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            if (user.email != null) {
                                Text(
                                    text = user.email ?: "",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val repo = com.example.data.repository.CasinoFirestoreRepository(context)
                                            loginViewModel.syncWithFirestore(repo)
                                            SoundAndHapticManager.playWin(haptic)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("SYNC FIRESTORE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        loginViewModel.signOut()
                                        SoundAndHapticManager.playClick(haptic)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("SIGN OUT", color = RubyRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        is AuthUiState.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Authenticating...", fontSize = 12.sp, color = TextPrimary)
                            }
                        }

                        is AuthUiState.Error -> {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = RubyRed,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { loginViewModel.clearError() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("DISMISS", fontSize = 11.sp, color = TextSecondary)
                                }
                                Button(
                                    onClick = {
                                        val serverClientId = context.getString(R.string.default_web_client_id)
                                        loginViewModel.launchGoogleSignIn(context, serverClientId)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Text("RETRY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        is AuthUiState.Idle -> {
                            var showEmailForm by remember { mutableStateOf(false) }
                            var emailInput by remember { mutableStateOf("") }
                            var passwordInput by remember { mutableStateOf("") }

                            Text(
                                text = "Sign in to synchronize your virtual chips, provably fair history, and VIP rank securely.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    SoundAndHapticManager.playClick(haptic)
                                    val serverClientId = context.getString(R.string.default_web_client_id)
                                    loginViewModel.launchGoogleSignIn(context, serverClientId)
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("SIGN IN WITH GOOGLE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { showEmailForm = !showEmailForm },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (showEmailForm) "Hide Email Sign-In" else "Or use Email & Password",
                                    fontSize = 12.sp,
                                    color = GoldLight
                                )
                            }

                            if (showEmailForm) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = emailInput,
                                        onValueChange = { emailInput = it },
                                        label = { Text("Email Address") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = CasinoCardBorder
                                        )
                                    )
                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = { passwordInput = it },
                                        label = { Text("Password (min 6 chars)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = CasinoCardBorder
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                SoundAndHapticManager.playClick(haptic)
                                                loginViewModel.signInWithEmail(emailInput, passwordInput)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("SIGN IN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                SoundAndHapticManager.playClick(haptic)
                                                loginViewModel.signUpWithEmail(emailInput, passwordInput)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("REGISTER", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // VIP Progress Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("VIP TIER PROGRESS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                        Text(
                            text = nextTier?.let { "Next: ${it.title}" } ?: "MAX TIER (DIAMOND)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progressToNextTier },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = GoldPrimary,
                        trackColor = Color(0xFF282D42)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Total volume: ${formatChips(userProfile.totalBets)} / ${formatChips(nextTier?.threshold ?: userProfile.totalBets)} chips wagered",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Stats Grid
        item {
            Column {
                Text("CAREER STATISTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("TOTAL WAGERED", formatChips(userProfile.totalBets), modifier = Modifier.weight(1f))
                    StatTile("TOTAL WON", formatChips(userProfile.totalWon), modifier = Modifier.weight(1f), isGreen = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("ROUNDS PLAYED", "${userProfile.roundsPlayed}", modifier = Modifier.weight(1f))
                    val net = userProfile.totalWon - userProfile.totalBets
                    StatTile("NET P/L", if (net >= 0) "+${formatChips(net)}" else formatChips(net), modifier = Modifier.weight(1f), isGreen = net >= 0)
                }
            }
        }

        // Provably Fair Custom Client Seed
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PROVABLY FAIR CLIENT SEED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)
                    Text(
                        text = "You can set your own random seed to mathematically guarantee the house cannot predict or influence your game results.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = clientSeedInput,
                            onValueChange = { clientSeedInput = it },
                            label = { Text("My Client Seed") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = CasinoCardBorder
                            )
                        )

                        Button(
                            onClick = {
                                CasinoRepository.updateClientSeed(clientSeedInput)
                                SoundAndHapticManager.playClick(haptic)
                                savedMessage = "Client seed updated!"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    savedMessage?.let { msg ->
                        Text(text = msg, fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Toggles & Preferences
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CasinoSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CasinoCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("SYSTEM SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldLight)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sound Effects & Chimes", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isSoundOn,
                            onCheckedChange = { CasinoRepository.toggleSound() },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldDark)
                        )
                    }

                    HorizontalDivider(color = CasinoCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration Feedback", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isHapticsOn,
                            onCheckedChange = { CasinoRepository.toggleHaptics() },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldDark)
                        )
                    }

                    HorizontalDivider(color = CasinoCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Responsible Gaming Portal", color = Color.White, fontSize = 13.sp)
                        TextButton(onClick = onOpenResponsibleGaming) {
                            Text("Open Limits & Tools", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, isGreen: Boolean = false) {
    Surface(
        modifier = modifier,
        color = CasinoSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (isGreen) EmeraldGreen else Color.White)
        }
    }
}
