package uz.likwer.zeroonetask4supportbot.backend

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.*
import com.pengrad.telegrambot.request.SendMessage
import jakarta.transaction.Transactional
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.bot.BotService
import uz.likwer.zeroonetask4supportbot.bot.Utils
import uz.likwer.zeroonetask4supportbot.bot.Utils.Companion.htmlBold
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface BotTools {
    fun isOperator(userId: Long): Boolean
    fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?>
    fun findActiveOperator(language: String): User?
    fun getQueuedSession(operator: User): QueueResponse?
    fun stopChat(operator: User)
    fun stopChatAndSearchUser(operator: User)
    fun breakOperator(operator: User)
    fun nextUser(operator: User)
    fun continueWork(operator: User)
    fun endWork(operator: User)
    fun getMsg(key: String, user: User): String
    fun getMsgKeyByValue(value: String, user: User): String
    fun processCommand(text: String?, user: User): Boolean
    fun startWork(operator: User)
    fun sendAskYourQuestionMsg(user: User)
    fun sendWrongNumberMsg(user: User)
    fun toAnotherOperator(operator: User)
    fun sendRateMsg(user: User, operator: User, session: Session)
    fun sendChatStoppedMsg(operator: User)
    fun sendWorkPausedMsg(operator: User)
    fun sendWorkEndedMsg(operator: User)
    fun sendWorkStartedMsg(operator: User)
    fun sendWorkContinuedMsg(operator: User)
    fun sendSearchingUserMsg(operator: User)
    fun sendChooseLangMsg(user: User)
    fun sendSharePhoneMsg(user: User)
}

@Service
class BotToolsImpl(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val messageSource: MessageSource,
    private val doubleOperatorRepository: DoubleOperatorRepository,
    private val botService: BotService,
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
            message.video() != null -> Pair(MessageType.VIDEO_NOTE, message.video().fileId())
            message.audio() != null -> Pair(MessageType.AUDIO, message.audio().fileId)
            message.contact() != null -> Pair(MessageType.CONTACT, null)
            message.location() != null -> Pair(MessageType.LOCATION, null)
            message.dice() != null -> Pair(MessageType.DICE, null)
            message.sticker() != null -> Pair(MessageType.STICKER, message.sticker().fileId())
            message.animation() != null -> Pair(MessageType.ANIMATION, message.animation().fileId())
            message.document() != null -> Pair(MessageType.DOCUMENT, message.document().fileId())
            else -> throw UnSupportedMessageType()
        }
    }


    @Transactional
    override fun processCommand(text: String?, user: User): Boolean {
        return text?.let {
            when (getMsgKeyByValue(text, user)) {
                "STOP_CHAT" -> stopChat(user)
                "NEXT_USER" -> nextUser(user)
                "SHORT_BREAK" -> breakOperator(user)
                "CONTINUE_WORK" -> continueWork(user)
                "END_WORK" -> endWork(user)
                "START_WORK" -> startWork(user)
                else -> return false
            }
            true
        } ?: false
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


    @Transactional
    override fun stopChat(operator: User) {
        val session = sessionRepository.findByOperatorIdAndStatus(operator.id, SessionStatus.BUSY)
        session?.let {
            val user = it.user

            it.status = SessionStatus.CLOSED
            operator.operatorStatus = OperatorStatus.ACTIVE
            it.operator = null
            userRepository.save(operator)
            user.state = UserState.ACTIVE_USER
            sessionRepository.save(it)
            userRepository.save(user)

            sendChatStoppedMsg(operator)
            sendRateMsg(user, operator, session)
            sendAskYourQuestionMsg(user)
        }
    }

    @Transactional
    override fun stopChatAndSearchUser(operator: User) {
        stopChat(operator)
        sendSearchingUserMsg(operator)
    }

    @Transactional
    override fun breakOperator(operator: User) {
        stopChat(operator)
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.PAUSED
            userRepository.save(operator)

            sendWorkPausedMsg(operator)
        }
    }

    override fun continueWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)

            sendWorkContinuedMsg(operator)
        }
    }

    override fun startWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)

            sendSearchingUserMsg(operator)
            sendWorkStartedMsg(operator)
        }
    }

    override fun endWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.INACTIVE
            userRepository.save(operator)

            sendWorkEndedMsg(operator)
        }
    }

    override fun toAnotherOperator(operator: User) {
        val session = sessionRepository.findLastSessionByOperatorId(operator.id)
        session?.let {
            if (!doubleOperatorRepository.existsByOperatorIdAndSessionId(operator.id, it.id!!)) {
                doubleOperatorRepository.save(DoubleOperator(operator, it))
            }
        }
    }

    override fun sendAskYourQuestionMsg(user: User) {
        bot().execute(
            SendMessage(user.id, botTools().getMsg("ASK_YOUR_QUESTION", user).htmlBold())
                .replyMarkup(ReplyKeyboardRemove())
                .parseMode(ParseMode.HTML)
        )
    }

    override fun sendWrongNumberMsg(user: User) {
        bot().execute(SendMessage(user.id, botTools().getMsg("WRONG_NUMBER", user)))
    }

    override fun sendRateMsg(user: User, operator: User, session: Session) {
        bot().execute(
            SendMessage(
                user.id,
                botTools().getMsg("OPERATOR_STOPPED_CHAT", operator) + "\n" +
                        botTools().getMsg("PLEASE_RATE_OPERATOR_WORK", operator)
            ).replyMarkup(
                InlineKeyboardMarkup(
                    InlineKeyboardButton("1").callbackData("rateS1" + session.id),
                    InlineKeyboardButton("2").callbackData("rateS2" + session.id),
                    InlineKeyboardButton("3").callbackData("rateS3" + session.id),
                    InlineKeyboardButton("4").callbackData("rateS4" + session.id),
                    InlineKeyboardButton("5").callbackData("rateS5" + session.id)
                )
            )
        )
    }

    override fun sendChatStoppedMsg(operator: User) {
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("CHAT_STOPPED", operator).htmlBold())
                .parseMode(ParseMode.HTML)
        )
    }

    override fun sendWorkPausedMsg(operator: User) {
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("WORK_PAUSED", operator).htmlBold())
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton(botTools().getMsg("CONTINUE_WORK", operator)),
                        KeyboardButton(botTools().getMsg("END_WORK", operator))
                    ).resizeKeyboard(true)
                ).parseMode(ParseMode.HTML)
        )
    }

    override fun sendWorkEndedMsg(operator: User) {
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("WORK_ENDED", operator))
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton(botTools().getMsg("START_WORK", operator))
                    ).resizeKeyboard(true)
                )
        )
    }

    override fun sendWorkStartedMsg(operator: User) {
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("WORK_STARTED", operator))
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton(botTools().getMsg("START_WORK", operator))
                    ).resizeKeyboard(true)
                )
        )
    }

    override fun sendWorkContinuedMsg(operator: User) {
        bot().execute(SendMessage(operator.id, botTools().getMsg("WORK_CONTINUED", operator)))
    }

    override fun sendSearchingUserMsg(operator: User) {
        bot().execute(SendMessage(operator.id, botTools().getMsg("SEARCHING_USER", operator).htmlBold()))
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

    override fun sendChooseLangMsg(user: User) {
        bot().execute(
            SendMessage(user.id, "Choose language")
                .replyMarkup(
                    InlineKeyboardMarkup(
                        InlineKeyboardButton(text = "🇺🇸", callbackData = "setLangEN"),
                        InlineKeyboardButton(text = "🇷🇺", callbackData = "setLangRU"),
                        InlineKeyboardButton(text = "🇺🇿", callbackData = "setLangUZ")
                    )
                )
        )
        user.state = UserState.CHOOSE_LANG
        userRepository.save(user)
    }

    override fun sendSharePhoneMsg(user: User) {
        bot().execute(
            SendMessage(user.id, getMsg("CLICK_TO_SEND_YOUR_PHONE", user))
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton(getMsg("SHARE_PHONE_NUMBER", user)).requestContact(true)
                    ).resizeKeyboard(true)
                )
        )
        user.state = UserState.SEND_PHONE_NUMBER
        userRepository.save(user)
    }

    override fun nextUser(operator: User) {

        val session = sessionRepository.findByOperatorIdAndStatus(operator.id, SessionStatus.BUSY)
        session?.let {

            val user = it.user
            operator.operatorStatus = OperatorStatus.ACTIVE
            session.status = SessionStatus.CLOSED
            user.state = UserState.ACTIVE_USER
            userRepository.save(user)
            sessionRepository.save(it)
            userRepository.save(operator)

            botService.contactActiveOperator(operator)

        }

    }
}
