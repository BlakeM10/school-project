package com.nextgen.courtvision.cv

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Real-time audio feedback (the FeedbackEngine class from the proposal's class
 * model): a distinct tone after each detected shot (made vs missed) and the
 * start cue for reaction drills. Visual feedback is the Live Session HUD.
 */
class FeedbackEngine {

    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    } catch (e: RuntimeException) {
        Log.w(TAG, "ToneGenerator unavailable — audio feedback disabled", e)
        null
    }

    fun playShotFeedback(made: Boolean) {
        val tone = if (made) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK
        toneGenerator?.startTone(tone, SHOT_TONE_MS)
    }

    fun playReactionCue() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, CUE_TONE_MS)
    }

    fun release() {
        toneGenerator?.release()
    }

    private companion object {
        const val TAG = "FeedbackEngine"
        const val VOLUME = 90
        const val SHOT_TONE_MS = 200
        const val CUE_TONE_MS = 150
    }
}
