package de.ka.rgreed.util

import com.google.firebase.messaging.*
import de.ka.rgreed.dao.model.ConsensusDao
import mu.KotlinLogging
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException

object FirebasePushUtil {

    private val logger = KotlinLogging.logger { }

    enum class PushType {
        CONSENSUS_FINISHED,
        CONSENSUS_VOTING_START_DATE_REACHED
    }

    private val firebaseInstance: FirebaseMessaging by lazy { FirebaseMessaging.getInstance() }

    @Throws(FirebaseMessagingException::class)
    fun sendPushToDevice(deviceToken: String, title: String, body: String, isDryRun: Boolean = false) {

        val message = Message.builder()
            .setToken(deviceToken)
            .putData("id", Math.random().toString())
            .putData("title", title)
            .putData("body", body)
            .build()

        firebaseInstance.send(message, isDryRun).also {
            logger.debug { it }
        }
    }

    @Throws(FirebaseMessagingException::class)
    fun sendPushTo(deviceTokens: List<String>, consensus: ConsensusDao, pushType: PushType, isDryRun: Boolean = false) {
        if (deviceTokens.isNotEmpty()) {
            val message = MulticastMessage.builder()
                .putData("type", pushType.name)
                .putData("consensusId", consensus.id.value.toString())
                .putData("consensusTitle", consensus.title)
                .putData("consensusDescription", consensus.description)
                .addAllTokens(deviceTokens).build()

            firebaseInstance.sendMulticast(message, isDryRun).also { response ->
                if (response.failureCount > 0) {
                    val failed = response.responses.filter { !it.isSuccessful }
                    logger.debug { "List of tokens that caused failures: $failed" }
                }
            }
        }
    }
}