package uz.likwer.zeroonetask4supportbot.backend

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove
import com.pengrad.telegrambot.request.SendMessage
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.bot.Utils
import java.util.concurrent.CopyOnWriteArrayList

interface BotTools {

    fun isOperator(userId: Long): Boolean
    fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?>
    fun findActiveOperator(language: String): User?
    fun getQueuedSession(operator: User): QueueResponse
    fun changeOperatorStatus(operator: User, status: OperatorStatus)
    fun stopChat(operator: User)
    fun breakOperator(operator: User)
    fun nextUser(operator: User)
    fun continueWork(operator: User)
    fun endWork(operator: User)

}

@Service
class BotToolsImpl(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) : BotTools {

    fun bot(): TelegramBot {
        return Utils.telegramBot()
    }

    override fun isOperator(userId: Long): Boolean {
        val user = userRepository.findById(userId)
        if (user.isPresent) {
            return (user.get().role == UserRole.OPERATOR)
        }
        return false
    }

    override fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?> {

        return when {
            message.text() != null -> Pair(MessageType.TEXT, null)
            message.photo() != null -> {
//                val photo = message.photo().maxByOrNull { it.fileSize() ?: 0 }
                val photo = message.photo()[3]
                Pair(MessageType.PHOTO, photo?.fileId())
            }

            message.voice() != null -> Pair(MessageType.VOICE, message.voice().fileId())
            message.video() != null -> Pair(MessageType.VIDEO, message.video().fileId())
            message.audio() != null -> Pair(MessageType.AUDIO, message.audio().fileId)
            message.contact() != null -> Pair(MessageType.CONTACT, null)
            message.location() != null -> Pair(MessageType.LOCATION, null)
            message.sticker() != null -> Pair(MessageType.STICKER, message.sticker().fileId())
            message.animation() != null -> Pair(MessageType.ANIMATION, message.animation().fileId())
            message.document() != null -> Pair(MessageType.DOCUMENT, message.document().fileId())
            else -> throw UnSupportedMessageType()
        }
    }

    override fun findActiveOperator(language: String): User? {
        return userRepository.findFirstByRoleAndOperatorStatusAndDeletedFalseOrderByModifiedDateAsc(
            UserRole.OPERATOR,
            OperatorStatus.ACTIVE
        )
    }

    @Synchronized
    override fun getQueuedSession(operator: User): QueueResponse {
        val languages = operator.languages
        val languageToQueueMap = mapOf(
            Language.UZ to DataLoader.queueUz,
            Language.RU to DataLoader.queueRu,
            Language.EN to DataLoader.queueEn
        )

        var smallestSession: Long? = null
        var smallestQueue: Map<Long, CopyOnWriteArrayList<Messages>>? = null

        for (language in languages) {
            val queue = languageToQueueMap[language]
            val currentSession = queue?.keys?.minOrNull()
            if (currentSession != null && (smallestSession == null || currentSession < smallestSession)) {
                smallestSession = currentSession
                smallestQueue = queue
            }
        }

        return smallestSession?.let {
            QueueResponse(it, smallestQueue!![it]!!)
        } ?: throw NoSessionInQueue()
    }


    override fun changeOperatorStatus(operator: User, status: OperatorStatus) {
        if (status == OperatorStatus.ACTIVE) {
            if (!true) operator.operatorStatus = status
        } else {
            operator.operatorStatus = status
        }
        userRepository.save(operator)
    }


    override fun stopChat(operator: User) {
        val session = sessionRepository.findByOperatorIdAndStatus(operator.id, SessionStatus.BUSY)
        session?.let {
            val user = it.user

            it.status = SessionStatus.CLOSED
            it.operator = null
            operator.operatorStatus = OperatorStatus.ACTIVE
            user.state = UserState.ACTIVE_USER
            bot().execute(
                SendMessage(user.id, "Operator stopped the chat\nPlease rate operator's work")
                    .replyMarkup(
                        InlineKeyboardMarkup(
                            InlineKeyboardButton("1").callbackData("rateS1" + session.id),
                            InlineKeyboardButton("2").callbackData("rateS2" + session.id),
                            InlineKeyboardButton("3").callbackData("rateS3" + session.id),
                            InlineKeyboardButton("4").callbackData("rateS4" + session.id),
                            InlineKeyboardButton("5").callbackData("rateS5" + session.id)
                        )
                    )
            )
            bot().execute(SendMessage(user.id, "Ask your question").replyMarkup(ReplyKeyboardRemove()))
        }
    }

    override fun breakOperator(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.PAUSED
            userRepository.save(operator)
            bot().execute(
                SendMessage(operator.id, "Work paused")
                    .replyMarkup(
                        ReplyKeyboardMarkup(
                            KeyboardButton("Continue work ⏸️"),
                            KeyboardButton("End work 🏠")
                        )
                    )
            )
        }
    }

    override fun continueWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)
            bot().execute(SendMessage(operator.id, "Work continued"))
        }
    }

    override fun endWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.INACTIVE
            userRepository.save(operator)
            bot().execute(
                SendMessage(operator.id, "Work ended 🏠")
                    .replyMarkup(
                        ReplyKeyboardMarkup(
                            KeyboardButton("Start work ✅")
                        )
                    )
            )
        }
    }

    override fun nextUser(operator: User) {

    }
}
