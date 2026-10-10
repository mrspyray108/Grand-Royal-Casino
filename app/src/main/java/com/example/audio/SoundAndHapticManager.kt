package com.example.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.data.repository.CasinoRepository

object SoundAndHapticManager {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        } catch (e: Exception) {
            Log.w("CasinoAudio", "ToneGenerator init error: ${e.message}")
        }
    }

    fun playClick(haptic: HapticFeedback? = null) {
        if (CasinoRepository.isHapticsEnabled.value) {
            haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
        } catch (_: Exception) {}
    }

    fun playChipPlace(haptic: HapticFeedback? = null) {
        if (CasinoRepository.isHapticsEnabled.value) {
            haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 40)
        } catch (_: Exception) {}
    }

    fun playSpin(haptic: HapticFeedback? = null) {
        if (CasinoRepository.isHapticsEnabled.value) {
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 80)
        } catch (_: Exception) {}
    }

    fun playWin(haptic: HapticFeedback? = null) {
        if (CasinoRepository.isHapticsEnabled.value) {
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
        } catch (_: Exception) {}
    }

    fun playBigWin(haptic: HapticFeedback? = null) {
        if (CasinoRepository.isHapticsEnabled.value) {
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
        } catch (_: Exception) {}
    }

    fun playCrash() {
        if (!CasinoRepository.isSoundEnabled.value) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 180)
        } catch (_: Exception) {}
    }
}
