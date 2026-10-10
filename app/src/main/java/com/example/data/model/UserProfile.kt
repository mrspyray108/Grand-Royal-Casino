package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * User preferences for gameplay, audio, and UI customizations.
 */
@IgnoreExtraProperties
data class UserPreferences(
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val soundVolume: Float = 0.8f,
    val theme: String = "dark",
    val emailNotifications: Boolean = false,
    val favoriteGame: String = "slots",
    val isPrivacyOptedOut: Boolean = false,
    val autoSpinLimit: Int = 10,
    val showOddsDisplay: Boolean = true
)

/**
 * UserProfile data class storing user display name, current chip balance, preferences,
 * and play-money statistics in Firestore.
 */
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "HighRoller77",
    val username: String = "HighRoller77",
    val chipBalance: Long = 10_000L,
    val balance: Long = 10_000L,
    val preferences: UserPreferences = UserPreferences(),
    val email: String = "player@grandroyale.casino",
    val vipTier: VipTier = VipTier.BRONZE,
    val totalBets: Long = 0L,
    val totalWon: Long = 0L,
    val roundsPlayed: Int = 0,
    val favoriteGameId: String = "slots",
    val avatarEmoji: String = "👑",
    val joinedTimestamp: Long = System.currentTimeMillis(),
    val isPrivacyOptedOut: Boolean = false,
    val clientSeed: String = "my-lucky-seed-2026",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun withUpdatedDisplayName(name: String): UserProfile =
        copy(displayName = name, username = name)

    fun withUpdatedBalance(newBalance: Long): UserProfile =
        copy(chipBalance = newBalance, balance = newBalance)
}
