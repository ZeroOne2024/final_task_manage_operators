package uz.likwer.zeroonetask4supportbot.backend

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove
import com.pengrad.telegrambot.request.SendMessage
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.bot.Utils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface BotTools {
    fun isOperator(userId: Long): Boolean
    fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?>
    fun findActiveOperator(language: String): User?
    fun getQueuedSession(operator: User): QueueResponse?
    fun stopChat(operator: User)
    fun breakOperator(operator: User)
    fun nextUser(operator: User)
    fun continueWork(operator: User)
    fun endWork(operator: User)
    fun getMsg(key: String, user: User): String
    fun getMsgKeyByValue(value: String, user: User): String

}

@Service
class BotToolsImpl(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val messageSource: MessageSource,
) : BotTools {
    fun bot(): TelegramBot {
        return Utils.telegramBot()
    }

    fun botTools(): BotTools {
        return Utils.botTools()
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
                val photo = message.photo().maxByOrNull { it.fileSize() ?: 0 }
                Pair(MessageType.PHOTO, photo?.fileId())
            }

            message.videoNote() != null -> Pair(MessageType.VIDEO, message.videoNote().fileId())
            message.voice() != null -> Pair(MessageType.VOICE, message.voice().fileId())
            message.video() != null -> Pair(MessageType.VIDEO, message.video().fileId())
            message.audio() != null -> Pair(MessageType.AUDIO, message.audio().fileId)
            message.contact() != null -> Pair(MessageType.CONTACT, null)
            message.location() != null -> Pair(MessageType.LOCATION, null)
            message.location() != null -> Pair(MessageType.DICE, null)
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
    override fun getQueuedSession(operator: User): QueueResponse? {

        val languages = operator.languages
        val languageToQueueMap = mapOf(
            Language.UZ to DataLoader.queueUz,
            Language.RU to DataLoader.queueRu,
            Language.EN to DataLoader.queueEn
        )

        var smallestSession: Long? = null
        var smallestQueue: ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>? = null

        for (language in languages) {
            val queue = languageToQueueMap[language]
            val currentSession = queue?.keys?.minOrNull()
            if (currentSession != null && (smallestSession == null || currentSession < smallestSession)) {
                smallestSession = currentSession
                smallestQueue = queue
            }
        }

        return smallestSession?.let { session ->
            smallestQueue?.let { queue ->
                val messages = queue.remove(session)
                if (messages != null) {
                    QueueResponse(session, messages)
                } else {
                    throw NoSessionInQueue()
                }
            }
        }
    }


    override fun stopChat(operator: User) {
        val session = sessionRepository.findByOperatorIdAndStatus(operator.id, SessionStatus.BUSY)
        session?.let {
            val user = it.user

            it.status = SessionStatus.CLOSED
//            it.operator = null
            operator.operatorStatus = OperatorStatus.ACTIVE
            user.state = UserState.ACTIVE_USER
            sessionRepository.save(it)
            userRepository.save(user)
            userRepository.save(operator)
            bot().execute(
                SendMessage(
                    user.id,
                    botTools().getMsg(
                        "OPERATOR_STOPPED_CHAT",
                        operator
                    ) + botTools().getMsg("PLEASE_RATE_OPERATOR_WORK", operator)
                )
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
            bot().execute(
                SendMessage(user.id, botTools().getMsg("ASK_YOUR_QUESTION", user)).replyMarkup(
                    ReplyKeyboardRemove()
                )
            )
        }
    }

    override fun breakOperator(operator: User) {
        stopChat(operator)
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.PAUSED
            userRepository.save(operator)
            bot().execute(
                SendMessage(operator.id, botTools().getMsg("WORK_PAUSED", operator))
                    .replyMarkup(
                        ReplyKeyboardMarkup(
                            KeyboardButton(botTools().getMsg("CONTINUE_WORK", operator)),
                            KeyboardButton(botTools().getMsg("END_WORK", operator))
                        )
                    )
            )
        }
    }

    override fun continueWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)
            bot().execute(SendMessage(operator.id, botTools().getMsg("WORK_CONTINUED", operator)))
        }
    }

    override fun endWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.INACTIVE
            userRepository.save(operator)
            bot().execute(
                SendMessage(operator.id, botTools().getMsg("WORK_ENDED", operator))
                    .replyMarkup(
                        ReplyKeyboardMarkup(
                            KeyboardButton(botTools().getMsg("START_WORK", operator))
                        )
                    )
            )
        }
    }

    override fun nextUser(operator: User) {
        //TODO
    }


    //translate functions
    override fun getMsg(key: String, user: User): String {
        val locale = Locale.forLanguageTag(user.languages[0].name.lowercase())
        return messageSource.getMessage(key, null, locale)
    }

    override fun getMsgKeyByValue(value: String, user: User): String {
        val locale = Locale.forLanguageTag(user.languages[0].name.lowercase())
        val bundle = ResourceBundle.getBundle("messages", locale)
        for (key in bundle.keySet())
            if (bundle.getString(key) == value)
                return key
        return ""
    }
}
