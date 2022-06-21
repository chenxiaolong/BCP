package com.chiller3.bcpsample

import android.content.Context
import android.media.*
import android.telecom.Call
import android.util.Log
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Plays back a sine wave to the telephony output device.
 *
 * @constructor Create a thread for audio playback. Note that the system only has a single telephony
 * output device. If multiple calls are active, unexpected behavior may occur.
 * @param context Used for accessing the [AudioManager]. A reference is kept in the object.
 * @param listener Used for sending completion notifications. The listener is called from this
 * thread, not the main thread.
 * @param call Used only for logging and is not saved.
 */
class PlayerThread(
    private val context: Context,
    private val listener: OnPlaybackCompletedListener,
    call: Call,
) : Thread(PlayerThread::class.java.simpleName) {
    private val tag = "${PlayerThread::class.java.simpleName}/${id}"

    // Thread state
    @Volatile private var isCancelled = false
    private var captureFailed = false

    init {
        Log.i(tag, "Created thread for call: $call")
    }

    override fun run() {
        var success = false
        var errorMsg: String? = null

        try {
            Log.i(tag, "Player thread started")

            if (isCancelled) {
                Log.i(tag, "Player cancelled before it began")
            } else {
                playUntilCancelled()

                success = !captureFailed
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during playback", e)
            errorMsg = e.localizedMessage
        } finally {
            Log.i(tag, "Player thread completed")

            if (success) {
                listener.onPlaybackCompleted(this)
            } else {
                listener.onPlaybackFailed(this, errorMsg)
            }
        }
    }

    /**
     * Cancel playback. This stops playing audio after processing the next minimum buffer size.
     *
     * If called before [start], no initialization of the audio output device will occur.
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * Play audio until [cancel] is called or an audio playback error occurs.
     */
    private fun playUntilCancelled() {
        // Find telephony output device
        val audioManager = context.getSystemService(AudioManager::class.java)
        val telephonyOutput = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .find { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
            ?: throw Exception("No telephony output audio device found")

        // Use mono PCM s16le output format
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_FRONT_LEFT)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBufSize = AudioTrack.getMinBufferSize(
            audioFormat.sampleRate,
            audioFormat.channelMask,
            audioFormat.encoding,
        )
        if (minBufSize < 0) {
            throw Exception("Failure when querying minimum buffer size: $minBufSize")
        }

        Log.d(tag, "AudioTrack minimum buffer size: $minBufSize")

        // Create audio output track
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()
        val audioTrack = AudioTrack.Builder()
            .setBufferSizeInBytes(minBufSize)
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormat)
            .build()

        Log.d(tag, "AudioTrack format: ${audioTrack.format}")

        try {
            if (!audioTrack.setPreferredDevice(telephonyOutput)) {
                throw Exception("Failed to set preferred output device")
            }

            audioTrack.play()
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                throw Exception("AudioTrack is in a bad state: ${audioTrack.playState}")
            }

            try {
                playbackLoop(audioTrack, minBufSize)
            } finally {
                audioTrack.stop()
            }
        } finally {
            audioTrack.release()
        }
    }

    /**
     * Main loop for playing back a sine wave.
     *
     * The loop runs forever until [cancel] is called. The approximate amount of time to cancel is
     * the time it takes to process the minimum buffer size.
     *
     * @param audioTrack [AudioTrack.play] must have been called
     * @param bufSizeInBytes Size of buffer to use for each [AudioTrack.write] operation
     *
     * @throws Exception if an error occurs during audio playback
     */
    private fun playbackLoop(audioTrack: AudioTrack, bufSizeInBytes: Int) {
        while (!isCancelled) {
            val buffer = ShortArray(bufSizeInBytes / 2)
            var pos = 0

            for (i in buffer.indices) {
                val normalizedSample = sin(2 * Math.PI * FREQUENCY * pos / audioTrack.sampleRate)
                buffer[i] = (normalizedSample * Short.MAX_VALUE).roundToInt().toShort()
                pos = (pos + 1) % audioTrack.sampleRate
            }

            val n = audioTrack.write(buffer, 0, buffer.size)
            if (n < 0) {
                throw Exception("Failed to write to audio track: $n")
            }
        }
    }

    companion object {
        const val FREQUENCY = 440
    }

    interface OnPlaybackCompletedListener {
        /**
         * Called when the playback completes successfully.
         */
        fun onPlaybackCompleted(thread: PlayerThread)

        /**
         * Called when an error occurs during playback.
         */
        fun onPlaybackFailed(thread: PlayerThread, errorMsg: String?)
    }
}