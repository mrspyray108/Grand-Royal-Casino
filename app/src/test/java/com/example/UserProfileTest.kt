package com.example

import com.example.data.model.UserPreferences
import com.example.data.model.UserProfile
import com.example.data.model.VipTier
import org.junit.Assert.*
import org.junit.Test

class UserProfileTest {

    @Test
    fun testUserProfileDefaults() {
        val profile = UserProfile()
        assertEquals("HighRoller77", profile.displayName)
        assertEquals("HighRoller77", profile.username)
        assertEquals(10_000L, profile.chipBalance)
        assertEquals(10_000L, profile.balance)
        assertNotNull(profile.preferences)
        assertTrue(profile.preferences.soundEnabled)
        assertTrue(profile.preferences.hapticEnabled)
        assertEquals("dark", profile.preferences.theme)
        assertEquals(VipTier.BRONZE, profile.vipTier)
    }

    @Test
    fun testUserProfileCustomValues() {
        val customPrefs = UserPreferences(
            soundEnabled = false,
            hapticEnabled = true,
            soundVolume = 0.5f,
            theme = "gold",
            favoriteGame = "roulette"
        )
        val profile = UserProfile(
            userId = "user_12345",
            displayName = "CasinoKing",
            chipBalance = 50_000L,
            preferences = customPrefs,
            vipTier = VipTier.SILVER
        )

        assertEquals("user_12345", profile.userId)
        assertEquals("CasinoKing", profile.displayName)
        assertEquals(50_000L, profile.chipBalance)
        assertFalse(profile.preferences.soundEnabled)
        assertTrue(profile.preferences.hapticEnabled)
        assertEquals("gold", profile.preferences.theme)
        assertEquals("roulette", profile.preferences.favoriteGame)
        assertEquals(VipTier.SILVER, profile.vipTier)
    }

    @Test
    fun testUserProfileUpdateHelpers() {
        val initial = UserProfile(displayName = "Player1", chipBalance = 1000L)
        val updatedName = initial.withUpdatedDisplayName("GrandMaster")
        assertEquals("GrandMaster", updatedName.displayName)
        assertEquals("GrandMaster", updatedName.username)

        val updatedBalance = initial.withUpdatedBalance(25_000L)
        assertEquals(25_000L, updatedBalance.chipBalance)
        assertEquals(25_000L, updatedBalance.balance)
    }

    @Test
    fun testUserPreferencesCustomization() {
        val prefs = UserPreferences(
            soundEnabled = true,
            hapticEnabled = false,
            soundVolume = 1.0f,
            isPrivacyOptedOut = true,
            autoSpinLimit = 25
        )
        assertTrue(prefs.soundEnabled)
        assertFalse(prefs.hapticEnabled)
        assertEquals(1.0f, prefs.soundVolume, 0.001f)
        assertTrue(prefs.isPrivacyOptedOut)
        assertEquals(25, prefs.autoSpinLimit)
    }
}
