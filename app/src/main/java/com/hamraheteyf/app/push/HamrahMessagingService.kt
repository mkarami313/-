package com.hamraheteyf.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes. We send data-only messages, so [onMessageReceived] runs for our code
 * to post the notification (via [PushNotifier], which dedupes on the server notification id)
 * both in the background and foreground.
 */
class HamrahMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notifId = data["notifId"] ?: message.messageId ?: return
        val title = data["title"] ?: message.notification?.title ?: "همراهِ طیف"
        val body = data["body"] ?: message.notification?.body ?: ""
        PushNotifier.show(applicationContext, notifId, title, body)
    }

    override fun onNewToken(token: String) {
        // Register the refreshed token with our server (no-op if not signed in).
        PushRegistration.onNewToken(applicationContext, token)
    }
}
