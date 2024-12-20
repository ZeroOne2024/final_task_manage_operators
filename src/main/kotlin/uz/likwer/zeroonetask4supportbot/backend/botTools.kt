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
import uz.likwer.zeroonetask4supportbot.component.SpringContext
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
    fun sendEnterYourFullName(user: User)
    fun getChooseLanguageReplyMarkup(user: User): InlineKeyboardMarkup
    fun getStatusEmojiByBoolean(t: Boolean): String
}

@Service
class BotToolsImpl(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val messageSource: MessageSource,
    private val doubleOperatorRepository: DoubleOperatorRepository,
    private val messageRepository: MessageRepository,
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
            if (user.isOperator()) {
                when (getMsgKeyByValue(text, user)) {
                    "STOP_CHAT" -> stopChat(user)
                    "NEXT_USER" -> nextUser(user)
                    "SHORT_BREAK" -> breakOperator(user)
                    "CONTINUE_WORK" -> continueWork(user)
                    "END_WORK" -> endWork(user)
                    "START_WORK" -> startWork(user)
                    "TO_ANOTHER_OPERATOR" -> toAnotherOperator(user)
                    else -> when (text) {
                        "/setlang" -> sendChooseLangMsg(user)
                        "/setname" -> sendEnterYourFullName(user)
                        else -> return false
                    }
                }
            } else {
                when (text) {
                    "/setlang" -> sendChooseLangMsg(user)
                    "/setname" -> sendEnterYourFullName(user)
                    else -> return false
                }
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

        return smallestSession?.takeIf { session ->
            !doubleOperatorRepository.existsByOperatorIdAndSessionId(operator.id, session)
        }?.let { session ->
            smallestQueue?.remove(session)?.let { messages ->
                QueueResponse(session, messages)
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
//            it.operator = null
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
            sendSearchingUserMsg(operator)
        }
    }

    override fun startWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)

            sendWorkStartedMsg(operator)
            sendSearchingUserMsg(operator)
        }
    }

    override fun endWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.INACTIVE
            userRepository.save(operator)

            sendWorkEndedMsg(operator)
        }
    }

    @Synchronized
    override fun toAnotherOperator(operator: User) {
        val session = sessionRepository.findLastSessionByOperatorId(operator.id)
        session?.let {
            if (!doubleOperatorRepository.existsByOperatorIdAndSessionId(operator.id, it.id!!)) {
                doubleOperatorRepository.save(DoubleOperator(operator, it))
            }

            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)
            session.status = SessionStatus.WAITING
            session.operator = null
            sessionRepository.save(session)
            val botService = SpringContext.getBean(BotService::class.java)
            val messages = messageRepository.findAllBySessionIdOrderByCreatedDateAsc(session.id!!)
            for (message in messages) {
                val translatedTextOperator = getMsg("OPERATOR", session.user)
                val translatedTextUser = getMsg("USER", session.user)
                val text = if (message.user.isOperator()) "$translatedTextOperator:\n"
                else "$translatedTextUser:\n"

                message.text?.let { t ->
                    message.text = text + t
                }

                message.caption?.let { c ->
                    message.caption = text + c
                }

                botService.addMessageToMap(session.id!!, message, session.user.languages[0].toString())
            }
            sendSearchingUserMsg(operator)
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
                InlineKeyboardMarkup()
                    .addRow(
                        InlineKeyboardButton(botTools().getMsg("VERY_BAD", user))
                            .callbackData("rateS1" + session.id)
                    )
                    .addRow(
                        InlineKeyboardButton(botTools().getMsg("BAD", user))
                            .callbackData("rateS2" + session.id)
                    )
                    .addRow(
                        InlineKeyboardButton(botTools().getMsg("SATISFACTORY", user))
                            .callbackData("rateS3" + session.id)
                    )
                    .addRow(
                        InlineKeyboardButton(botTools().getMsg("GOOD", user))
                            .callbackData("rateS4" + session.id)
                    )
                    .addRow(
                        InlineKeyboardButton(botTools().getMsg("EXCELLENT", user))
                            .callbackData("rateS5" + session.id)
                    )
            )
        )
    }

    override fun sendChatStoppedMsg(operator: User) {
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("CHAT_STOPPED", operator).htmlBold())
                .parseMode(ParseMode.HTML)
                .replyMarkup(ReplyKeyboardRemove())
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
        bot().execute(
            SendMessage(operator.id, botTools().getMsg("SEARCHING_USER", operator).htmlBold())
                .parseMode(ParseMode.HTML)
                .replyMarkup(ReplyKeyboardRemove())
        )
    }

    //translate functions
    override fun getMsg(key: String, user: User): String {
        try {
            val locale = Locale.forLanguageTag(user.languages[0].name.lowercase())
            return messageSource.getMessage(key, null, locale)
        } catch (e: Exception) {
            return "Error"
        }

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
        if (user.isOperator()) {
            val translatedTextChooseLanguage = botTools().getMsg("CHOOSE_LANGUAGE", user)
            val msgId = bot().execute(
                SendMessage(user.id, translatedTextChooseLanguage)
                    .replyMarkup(botTools().getChooseLanguageReplyMarkup(user))
            ).message().messageId()
            user.msgIdChooseLanguage = msgId
        } else {
            // isUser
            val msgId = bot().execute(
                SendMessage(user.id, "Choose language")
                    .replyMarkup(
                        InlineKeyboardMarkup(
                            InlineKeyboardButton(text = "🇺🇸", callbackData = "setLangEN"),
                            InlineKeyboardButton(text = "🇷🇺", callbackData = "setLangRU"),
                            InlineKeyboardButton(text = "🇺🇿", callbackData = "setLangUZ")
                        )
                    )
            ).message().messageId()
            user.msgIdChooseLanguage = msgId
            user.state = UserState.CHOOSE_LANG
        }
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

    override fun sendEnterYourFullName(user: User) {
        bot().execute(SendMessage(user.id, botTools().getMsg("SEND_YOUR_FULL_NAME", user)).replyMarkup(ReplyKeyboardRemove()))
        user.state = UserState.SEND_FULL_NAME
        userRepository.save(user)
    }

    override fun getChooseLanguageReplyMarkup(user: User): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            InlineKeyboardButton(
                text = "🇺🇸 ${getStatusEmojiByBoolean(user.languages.contains(Language.EN))}",
                callbackData = "setLangEN"
            ),
            InlineKeyboardButton(
                text = "🇷🇺 ${getStatusEmojiByBoolean(user.languages.contains(Language.RU))}",
                callbackData = "setLangRU"
            ),
            InlineKeyboardButton(
                text = "🇺🇿 ${getStatusEmojiByBoolean(user.languages.contains(Language.UZ))}",
                callbackData = "setLangUZ"
            )
        )
    }

    override fun getStatusEmojiByBoolean(t: Boolean): String {
        return if (t) "✅" else "❌"
    }

    @Transactional
    override fun nextUser(operator: User) {

        stopChat(operator)

        val botService = SpringContext.getBean(BotService::class.java)

        sendSearchingUserMsg(operator)
        botService.contactActiveOperator(operator)

    }

}
