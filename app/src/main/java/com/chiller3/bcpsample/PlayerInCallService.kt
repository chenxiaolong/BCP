package com.chiller3.bcpsample

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class PlayerInCallService : InCallService(), PlayerThread.OnPlaybackCompletedListener {
    companion object {
        private val TAG = PlayerInCallService::class.java.simpleName
    }

    private lateinit var prefs: Preferences
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Player threads for each active call. When a call is disconnected, it is immediately removed
     * from this map and [pendingExit] is incremented.
     */
    private val players = HashMap<Call, PlayerThread>()

    /**
     * Number of threads pending exit after the call has been disconnected. This can be negative if
     * the player thread fails before the call is disconnected.
     */
    private var pendingExit = 0

    private var failedNotificationId = 2

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, $state")

            handleStateChange(call)
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = Preferences(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")

        call.registerCallback(callback)
        handleStateChange(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")

        call.unregisterCallback(callback)
        handleStateChange(call)
    }

    /**
     * Start a new player thread when a call becomes active and cancel it when it disconnects.
     *
     * When a call disconnects, the call is removed from [players] and [pendingExit] is incremented.
     * [pendingExit] gets decremented when the thread actually completes, which may be before the
     * call disconnects if an error occurred.
     */
    private fun handleStateChange(call: Call) {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }

        if (state == Call.STATE_ACTIVE) {
            if (!prefs.isEnabled) {
                Log.v(TAG, "Call audio playback is disabled")
            } else if (!Permissions.haveRequired(this)) {
                Log.v(TAG, "Required permissions have not been granted")
            } else if (!players.containsKey(call)) {
                val player = PlayerThread(this, this, call)
                players[call] = player

                updateForegroundState()
                player.start()
            }
        } else if (state == Call.STATE_DISCONNECTING || state == Call.STATE_DISCONNECTED) {
            val player = players[call]
            if (player != null) {
                player.cancel()

                players.remove(call)

                // Don't change the foreground state until the thread has exited
                ++pendingExit
            }
        }
    }

    /**
     * Move to foreground, creating a persistent notification, when there are active calls or player
     * threads that haven't finished exiting yet.
     */
    private fun updateForegroundState() {
        if (players.isEmpty() && pendingExit == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            startForeground(1, createPersistentNotification())
        }
    }

    /**
     * Create a persistent notification for use during playback. The notification appearance is
     * fully static and in progress audio playback is represented by the presence or absence of the
     * notification.
     */
    private fun createPersistentNotification(): Notification {
        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, PlayerApplication.CHANNEL_ID_PERSISTENT).run {
            setContentTitle(getText(R.string.notification_playback_in_progress))
            setSmallIcon(R.drawable.ic_launcher_notification)
            setContentIntent(pendingIntent)
            setOngoing(true)

            // Inhibit 10-second delay when showing persistent notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            build()
        }
    }

    private fun createPlaybackFailedNotification(errorMsg: String?): Notification =
        Notification.Builder(this, PlayerApplication.CHANNEL_ID_ALERTS).run {
            val text = buildString {
                val errorMsgTrimmed = errorMsg?.trim()
                if (!errorMsgTrimmed.isNullOrBlank()) {
                    append(errorMsgTrimmed)
                }
            }

            setContentTitle(getString(R.string.notification_playback_failed))
            if (text.isNotBlank()) {
                setContentText(text)
                style = Notification.BigTextStyle()
            }
            setSmallIcon(R.drawable.ic_launcher_notification)

            build()
        }

    private fun onThreadExited() {
        --pendingExit
        updateForegroundState()
    }

    override fun onPlaybackCompleted(thread: PlayerThread) {
        Log.i(TAG, "Playback completed: ${thread.id}")
        handler.post {
            onThreadExited()
        }
    }

    override fun onPlaybackFailed(thread: PlayerThread, errorMsg: String?) {
        Log.w(TAG, "Playback failed: ${thread.id}: $errorMsg")
        handler.post {
            onThreadExited()

            val notification = createPlaybackFailedNotification(errorMsg)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(failedNotificationId, notification)
            ++failedNotificationId
        }
    }
}