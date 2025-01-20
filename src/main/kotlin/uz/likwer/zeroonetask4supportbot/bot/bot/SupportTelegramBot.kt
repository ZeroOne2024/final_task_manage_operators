package uz.likwer.zeroonetask4supportbot.bot.bot

import org.springframework.context.MessageSource
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.CopyMessage
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.bots.AbsSender
import uz.likwer.zeroonetask4supportbot.bot.bot.Utils.Companion.clearPhone
import uz.likwer.zeroonetask4supportbot.bot.bot.Utils.Companion.htmlBold
import uz.likwer.zeroonetask4supportbot.bot.backend.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors

open class SupportTelegramBot(
    var username: String,
    token: String,
    var botId: Long,

    private val userRepository: UserRepository,
    private val botMessageRepository: BotMessageRepository,
    private val locationRepository: LocationRepository,
    private val contactRepository: ContactRepository,
    private val diceRepository: DiceRepository,
    private val sessionRepository: SessionRepository,
    private val messageSource: MessageSource,
    private val doubleOperatorRepository: DoubleOperatorRepository,
    private val executorService: Executor = Executors.newFixedThreadPool(20),
    val queueEn: ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>> = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>(),
    val queueUz: ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>> = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>(),
    val queueRu: ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>> = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>()
) : TelegramLongPollingBot(token) {
    companion object {
        val activeBots = mutableMapOf<String, SupportTelegramBot>()
    }

    override fun getBotUsername() = username

    @Transactional
    override fun onUpdateReceived(update: Update) {
        executorService.execute {
            try {
                if (update.hasMessage())
                    handleMessage(update)
                if (update.hasEditedMessage())
                    handleEditedMessage(update)
                if (update.hasCallbackQuery())
                    handleCallbackQuery(update)
//                update.chatMember.newChatMember.status = kicked
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Transactional
    open fun handleMessage(update: Update) {
        val message = update.message

        getUser(message.from)?.let { user ->
            sendActionTyping(user)

            if (user.isUser()) {
                handleUserMessage(update, user)
            } else if (user.isOperator()) {
                handleOperatorMessage(update, user)
            }
        }
    }

    @Transactional
    open fun handleOperatorMessage(update: Update, operator: User) {
        val message = update.message
        if (operator.isTalking()) {
            sessionRepository.findLastSessionByOperatorId(operator.id)?.let { session ->
                if (session.botId != botId) {
                    findBotById(session.botId)?.let { bot ->
                        this.execute(
                            SendMessage(
                                operator.id.toString(),
                                getMsg("YOU_HAVE_ALREADY_OPENED_A_SESSION_IN_ANOTHER_BOT", operator) +
                                        " @${bot.username}"
                            )
                        )
                    }
                } else {
                    var isCommand = false
                    if (message.hasText())
                        isCommand = handleOperatorCommands(message.text, operator)
                    if (!isCommand)
                        handleSessionMsgForOperator(update, operator)
                }
                null
            }
        } else {
            when (operator.operatorStatus) {
                OperatorStatus.ACTIVE -> {
                    operator.botId = botId
                    userRepository.save(operator)
                    handleOperatorCommandsOrSendMainMenuMsg(message, operator)
                }

                OperatorStatus.INACTIVE -> {
                    handleOperatorCommandsOrSendMainMenuMsg(message, operator)
                }

                OperatorStatus.PAUSED -> {
                    handleOperatorCommandsOrSendMainMenuMsg(message, operator)
                }

                OperatorStatus.BUSY -> {}
                else -> {}
            }
        }
    }

    @Transactional
    open fun handleOperatorCommandsOrSendMainMenuMsg(message: Message, operator: User) {
        if (message.hasText()) {
            val isCommand = handleOperatorCommands(message.text, operator)
            if (!isCommand)
                sendMainMenuMsg(operator)
        }
    }

    @Transactional
    open fun handleUserMessage(update: Update, user: User) {
        val chatId = user.id
        val message = update.message

        when (user.state) {
            UserStateEnum.NEW_USER -> {
                handleStartCommand(user)
            }

            UserStateEnum.SEND_PHONE_NUMBER -> {
                if (message.hasContact()) {
                    val contact = message.contact
                    val phoneNumber = contact.phoneNumber.clearPhone()

                    if (contact.userId != chatId) {
                        handleInvalidPhoneNumber(user)
                    } else {
                        saveUserPhoneNumber(user, phoneNumber)
                        sendEnterYourFullName(user)
                    }
                } else {
                    sendSharePhoneMsg(user)
                }
            }

            UserStateEnum.SEND_FULL_NAME -> {
                if (message.hasText()) {
                    val text = message.text
                    updateUserFullName(user, text)
                    sendFullNameSavedMsg(user)
                } else {
                    sendEnterYourFullName(user)
                }
            }

            UserStateEnum.CHOOSE_LANG -> {
                sendChooseLangMsg(user)
            }

            UserStateEnum.TALKING -> {
                checkAndHandleUserSession(user, update)
            }

            UserStateEnum.ACTIVE_USER -> {
                if (message.hasText()) {
                    val text = message.text
                    when (getMsgKeyByValue(text, user)) {
                        "CONNECT_WITH_OPERATOR" -> {
                            sendAskYourQuestionMsg(user)
                        }

                        else -> {
                            if (!handleCommonCommands(text, user)) sendMainMenuMsg(user)
                        }
                    }
                } else sendMainMenuMsg(user)
            }

            UserStateEnum.ASK_YOUR_QUESTION -> {
                user.state = UserStateEnum.WAITING_OPERATOR
                userRepository.save(user)
                handleSessionMsgForUser(update, user)
            }

            UserStateEnum.WAITING_OPERATOR -> {
                checkAndHandleUserSession(user, update)
            }
        }
    }

    @Transactional
    open fun checkAndHandleUserSession(user: User, update: Update) {
        sessionRepository.findLastSessionByUserId(user.id)?.let { session ->
            if (session.botId != botId) {
                findBotById(session.botId)?.let { bot ->
                    this.execute(
                        SendMessage(
                            user.id.toString(),
                            getMsg("YOU_HAVE_ALREADY_OPENED_A_SESSION_IN_ANOTHER_BOT", user) +
                                    " @${bot.username}"
                        )
                    )
                }
            } else handleSessionMsgForUser(update, user)
        }
    }

    private fun findBotById(botId: Long): SupportTelegramBot? {
        for (bot in activeBots)
            if (bot.value.botId == botId)
                return bot.value
        return null
    }

    private fun sendMainMenuMsg(user: User) {
        val newText = """
            /setLang - ${getMsg("SET_LANG", user)}
        """.trimIndent()
        val sendMessage = SendMessage(user.id.toString(), newText)
        if (user.isUser()) {
            val row1 = KeyboardRow(1)
            row1.add(KeyboardButton(getMsg("CONNECT_WITH_OPERATOR", user)))
            val markup = ReplyKeyboardMarkup(listOf(row1))
            markup.resizeKeyboard = true
            sendMessage.replyMarkup = markup
        }
        this.execute(sendMessage)
    }

    private fun sendFullNameSavedMsg(user: User) {
        val sendMessage = SendMessage(user.id.toString(), getMsg("FULL_NAME_SAVED", user))
        val row1 = KeyboardRow(1)
        row1.add(KeyboardButton(getMsg("CONNECT_WITH_OPERATOR", user)))
        val markup = ReplyKeyboardMarkup(listOf(row1))
        markup.resizeKeyboard = true
        sendMessage.replyMarkup = markup
        this.execute(sendMessage)
    }

    private fun sendActionTyping(user: User) {
        this.execute(SendChatAction(user.id.toString(), "typing", null))
    }

    @Transactional
    open fun handleSessionMsgForOperator(update: Update, operator: User) {
        getOperatorSession(operator.id)?.let { session ->
            val savedMessage = newSessionMsg(update, session, operator)
            if (session.operator!!.botId == session.user.botId) {
                sendMessageToUser(session.user, savedMessage, session, this)
            }
        }
    }

    @Transactional
    open fun handleSessionMsgForUser(update: Update, user: User) {
        getSession(user).let { session ->
            val savedMessage = newSessionMsg(update, session, user)

            if (session.hasOperator()) {
                if (session.operator!!.botId == session.user.botId) {
                    sendMessageToUser(session.operator!!, savedMessage, session, this)
                }
            } else {
                user.botId = botId
                userRepository.save(user)
                addMessageToMap(
                    session.id!!,
                    savedMessage,
                    session.user.languages.elementAt(0).toString()
                )
            }
        }
    }

    private fun saveLocation(message: Message): Location? {
        return if (message.hasLocation()) {
            val loc = message.location
            locationRepository.save(Location(loc.latitude.toFloat(), loc.longitude.toFloat()))
        } else null
    }

    private fun saveContact(message: Message): Contact? {
        return if (message.hasContact()) {
            val contact = message.contact
            contactRepository.save(Contact(contact.firstName, contact.phoneNumber))
        } else null
    }

    private fun saveDice(message: Message): Dice? {
        return if (message.hasDice()) {
            val dice = message.dice
            diceRepository.save(Dice(dice.value, dice.emoji))
        } else null
    }

    private fun newSessionMsg(update: Update, session: Session, user: User): Messages {
        val message = update.message
        val messageReplyId = if (message.isReply) message.replyToMessage.messageId else null
        val typeAndFileId = determineMessageType(message)
        val location = saveLocation(message)
        val contact = saveContact(message)
        val dice = saveDice(message)

        return botMessageRepository.save(
            Messages(
                user = user,
                session = session,
                messageId = message.messageId,
                replyMessageId = messageReplyId,
                botMessageType = typeAndFileId.first,
                text = message.text,
                caption = message.caption,
                fileId = typeAndFileId.second,
                location = location,
                contact = contact,
                dice = dice,
                data = message.toString()
            )
        )
    }

    private fun saveUserPhoneNumber(user: User, phoneNumber: String) {
        user.phoneNumber = phoneNumber
        userRepository.save(user)
    }

    private fun handleInvalidPhoneNumber(user: User) {
        sendWrongNumberMsg(user)
    }

    private fun handleStartCommand(user: User) {
        if (user.languages.isEmpty()) {
            sendChooseLangMsg(user)
        } else {
            sendAskYourQuestionMsg(user)
        }
    }

    private fun updateUserFullName(user: User, text: String) {
        user.fullName = text
        user.state = UserStateEnum.ACTIVE_USER
        userRepository.save(user)
    }

    @Transactional
    open fun handleEditedMessage(update: Update) {
        val editedMessage = update.editedMessage
        val chatId = editedMessage.from.id
        val messageId = editedMessage.messageId
        val newText = editedMessage.text
        val newCaption = editedMessage.caption

        editMessage(chatId, messageId, newText, newCaption)
    }

    @Transactional
    open fun handleCallbackQuery(update: Update) {
        val callbackQuery = update.callbackQuery
        var data = callbackQuery.data

        getUser(callbackQuery.from)?.let { user ->
            val chatId = user.id

            if (user.isUser()) {
                when (user.state) {
                    UserStateEnum.NEW_USER -> {}
                    UserStateEnum.SEND_PHONE_NUMBER -> {}
                    UserStateEnum.SEND_FULL_NAME -> {}
                    UserStateEnum.ACTIVE_USER -> {}
                    UserStateEnum.ASK_YOUR_QUESTION -> {}
                    UserStateEnum.TALKING -> {}
                    UserStateEnum.WAITING_OPERATOR -> {}
                    UserStateEnum.CHOOSE_LANG -> {
                        if (data.startsWith("setLang", ignoreCase = true)) {
                            val lang = LanguageEnum.valueOf(data.uppercase().substring("setlang".length))

                            user.languages = mutableSetOf(lang)
                            user.state = UserStateEnum.ACTIVE_USER
                            userRepository.save(user)
                            this.execute(DeleteMessage(chatId.toString(), callbackQuery.message.messageId))

                            if (user.phoneNumber.isEmpty()) {
                                sendSharePhoneMsg(user)
                            } else
                                sendMainMenuMsg(user)
                        }
                    }
                }
                if (data.startsWith("rateS")) {
                    data = data.substring("rateS".length)
                    val rate = data.substring(0, 1).toShort()
                    val sessionId = data.substring(1).toLong()

                    setRate(sessionId, rate)
                    this.execute(
                        AnswerCallbackQuery(
                            callbackQuery.id, getMsg("THANK_YOU", user), false, null, null
                        )
                    )
                    this.execute(DeleteMessage(chatId.toString(), callbackQuery.message.messageId))
                }
            } else if (user.isOperator()) {
                var userCommand = false
                when (user.state) {
                    UserStateEnum.NEW_USER -> {
                        userCommand = true
                    }

                    UserStateEnum.SEND_PHONE_NUMBER -> {
                        userCommand = true
                    }

                    UserStateEnum.SEND_FULL_NAME -> {
                        userCommand = true
                    }

                    UserStateEnum.ACTIVE_USER -> {
                        userCommand = true
                    }

                    UserStateEnum.ASK_YOUR_QUESTION -> {
                        userCommand = true
                    }

                    UserStateEnum.TALKING -> {
                        userCommand = true
                    }

                    UserStateEnum.WAITING_OPERATOR -> {
                        userCommand = true
                    }

                    UserStateEnum.CHOOSE_LANG -> {
                        if (data.startsWith("setLang", ignoreCase = true)) {
                            val lang = LanguageEnum.valueOf(data.uppercase().substring("setlang".length))

                            if (user.languages.contains(lang)) {
                                if (user.languages.size != 1) user.languages.remove(lang)
                            } else user.languages.add(lang)

                            user.state = UserStateEnum.ACTIVE_USER
                            userRepository.save(user)
                            user.msgIdChooseLanguage?.let { msgId ->
                                this.execute(
                                    EditMessageReplyMarkup(
                                        chatId.toString(), msgId, null, getChooseLanguageReplyMarkup(user)
                                    )
                                )
                            }
                            if (user.phoneNumber.isEmpty()) {
                                sendSharePhoneMsg(user)
                            }
                        }
                        userCommand = true
                    }
                }
                if (!userCommand) {
                    when (user.operatorStatus) {
                        OperatorStatus.ACTIVE -> {}
                        OperatorStatus.INACTIVE -> {}
                        OperatorStatus.BUSY -> {}
                        OperatorStatus.PAUSED -> {}
                        null -> {}
                    }
                }
            }
        }
    }

    @Transactional
    open fun editMessage(chatId: Long, messageId: Int, newText: String?, newCaption: String?) {

        val message = botMessageRepository.findByUserIdAndMessageId(chatId, messageId)
            ?: throw IllegalArgumentException("Message with ID $messageId not found")

        if (!newText.isNullOrBlank() && message.botMessageType == BotMessageType.TEXT) {
            message.text = newText
            if (message.messageBotId != null) {
                if (message.session.user.id == chatId) {
                    val editMessage = EditMessageText(newText)
                    editMessage.messageId = message.messageBotId!!
                    editMessage.chatId = message.session.operator?.id.toString()
                    this.execute(editMessage)
                } else {
                    val editMessage = EditMessageText(newText)
                    editMessage.messageId = message.messageBotId!!
                    editMessage.chatId = message.session.user.id.toString()
                    this.execute(editMessage)
                }
            }
        }

        if (!newCaption.isNullOrBlank() && message.botMessageType in listOf(
                BotMessageType.PHOTO, BotMessageType.VIDEO, BotMessageType.DOCUMENT, BotMessageType.ANIMATION
            )
        ) {
            message.caption = newCaption
            if (message.messageBotId != null) {
                val editMessage = EditMessageCaption()
                editMessage.caption = newCaption
                editMessage.messageId = message.messageBotId!!
                if (message.session.user.id == chatId) {
                    editMessage.chatId = message.session.operator?.id.toString()
                } else {
                    editMessage.chatId = message.session.user.id.toString()
                }
                this.execute(editMessage)
            }
        }
        botMessageRepository.save(message)
    }

    @Synchronized
    fun getUser(from: org.telegram.telegrambots.meta.api.objects.User): User? {
        val userOpt = userRepository.findById(from.id)
        if (userOpt.isPresent) {
            if (userOpt.get().deleted) return null
            return userOpt.get()
        }
        var username = from.userName
        if (username == null) username = ""
        var lastName = from.lastName
        lastName = if (lastName == null) "" else " $lastName"
        return userRepository.save(
            User(
                from.id,
                username,
                from.firstName + lastName,
                "",
                botId
            )
        )
    }

    private fun sendMessageToUser(user: User, message: Messages, session: Session, absSender: AbsSender) {
        val replyMessageId = message.replyMessageId?.let { replyId ->
            botMessageRepository.findBySessionIdAndMessageBotId(session.id!!, replyId)?.messageId
                ?: botMessageRepository.findBySessionIdAndMessageId(session.id!!, replyId)?.messageBotId
        }
        val chatId = user.id.toString()
        val copyMessage = CopyMessage(chatId, message.user.id.toString(), message.messageId)
        replyMessageId?.let { copyMessage.replyToMessageId = it }
        if (!message.caption.isNullOrEmpty()) copyMessage.caption = message.caption
        val messageId: Long = absSender.execute(copyMessage).messageId
        message.messageBotId = messageId.toInt()
        message.deleted = true
        botMessageRepository.save(message)
    }


    @Synchronized
    fun addMessageToMap(sessionId: Long, message: Messages, language: String) {
        val targetQueue = when (language.lowercase()) {
            "en" -> queueEn
            "uz" -> queueUz
            "ru" -> queueRu
            else -> null
        }

        targetQueue?.let {
            it.compute(sessionId) { _, existingMessages ->
                val messagesList = existingMessages ?: CopyOnWriteArrayList()
                messagesList.add(message)
                messagesList
            }
        }
    }

    @Transactional
    open fun getSession(user: User): Session {
        val session = sessionRepository.findLastSessionByUserId(user.id)
        return if (session != null) {
            if (session.isClosed()) {
                this.execute(
                    SendMessage(
                        user.id.toString(), getMsg("THE_OPERATOR_WILL_ANSWER_YOU_SOON", user)
                    )
                )
                user.let { sessionRepository.save(Session(it, botId)) }
            } else {
                session
            }
        } else {
            this.execute(SendMessage(user.id.toString(), getMsg("THE_OPERATOR_WILL_ANSWER_YOU_SOON", user)))
            user.let { sessionRepository.save(Session(it, botId)) }
        }
    }

    private fun getOperatorSession(operatorId: Long): Session? {
        return sessionRepository.findLastSessionByOperatorId(operatorId)
            ?.takeIf { !it.isClosed() }
    }


    @Transactional
    open fun setRate(sessionId: Long, rate: Short): Session? {
        return sessionRepository.findByIdAndDeletedFalse(sessionId)?.let {
            if (it.status == SessionStatusEnum.CLOSED) {
                it.rate = rate
                sessionRepository.save(it)
            }
            it
        }
    }


    private fun sendUserInfoForOperator(operator: User, user: User) {
        val row1 = KeyboardRow(1)
        val row2 = KeyboardRow(2)
        val row3 = KeyboardRow(1)
        row1.add(KeyboardButton(getMsg("STOP_CHAT", operator)))
        row2.add(KeyboardButton(getMsg("NEXT_USER", operator)))
        row2.add(KeyboardButton(getMsg("TO_ANOTHER_OPERATOR", operator)))
        row3.add(KeyboardButton(getMsg("SHORT_BREAK", operator)))
        val replyKeyboardMarkup = ReplyKeyboardMarkup(mutableListOf(row1, row2, row3))
        replyKeyboardMarkup.resizeKeyboard = true
        replyKeyboardMarkup.inputFieldPlaceholder = "test"

        val userPhone = "+" + user.phoneNumber.clearPhone()
        val userText = getMsg("USER", operator)
        val userName = user.fullName
        val t = "$userText: $userName"
        val text = "$t\n" + userPhone

        val userNameMsgEnt = MessageEntity("", userText.length + 2, userName.length)
        if (user.username.isEmpty()) {
            userNameMsgEnt.type = "text_mention"
            userNameMsgEnt.user = org.telegram.telegrambots.meta.api.objects.User(user.id, user.fullName, false)
        } else {
            userNameMsgEnt.type = "text_link"
            userNameMsgEnt.url = "t.me/${user.username}"
        }

        val userPhoneMsgEnt = MessageEntity("phone_number", text.lines()[0].length, userPhone.length)

        val sendMessage = SendMessage(operator.id.toString(), text)
        sendMessage.entities = listOf(userNameMsgEnt, userPhoneMsgEnt)
        sendMessage.disableWebPagePreview = true
        sendMessage.replyMarkup = replyKeyboardMarkup
        this.execute(sendMessage)

    }

    @Synchronized
    fun contactActiveOperator(operator: User): Boolean {
        val queuedSession = getQueuedSession(operator)
        queuedSession?.let {
            var session = sessionRepository.findByIdAndDeletedFalse(queuedSession.sessionId)
            if (session != null) {
                if (operator.id != session.user.id) {
                    session.operator = operator
                    session.status = SessionStatusEnum.BUSY
                    session = sessionRepository.save(session)

                    operator.operatorStatus = OperatorStatus.BUSY
                    val saved = userRepository.save(operator)

                    sendUserInfoForOperator(operator, session.user)

                    val secondBot = findBotById(operator.botId)
                    this.execute(SendMessage(operator.id.toString(), ""))
                    for (message in queuedSession.messages) {
                        sendMessageToUser(saved, message, session, this)
                    }
                    return true
                }
            }
        }
        return false
    }

    open fun determineMessageType(message: Message): Pair<BotMessageType, String?> {
        return when {
            message.hasText() -> Pair(BotMessageType.TEXT, null)
            message.hasPhoto() -> {
                Pair(BotMessageType.PHOTO, message.photo.maxByOrNull { it.fileSize ?: 0 }?.fileId)
            }

            message.hasVideoNote() -> Pair(BotMessageType.VIDEO, message.videoNote.fileId)
            message.hasVoice() -> Pair(BotMessageType.VOICE, message.voice.fileId)
            message.hasVideo() -> Pair(BotMessageType.VIDEO_NOTE, message.video.fileId)
            message.hasAudio() -> Pair(BotMessageType.AUDIO, message.audio.fileId)
            message.hasContact() -> Pair(BotMessageType.CONTACT, null)
            message.hasLocation() -> Pair(BotMessageType.LOCATION, null)
            message.hasDice() -> Pair(BotMessageType.DICE, null)
            message.hasSticker() -> Pair(BotMessageType.STICKER, message.sticker.fileId)
            message.hasAnimation() -> Pair(BotMessageType.ANIMATION, message.animation.fileId)
            message.hasDocument() -> Pair(BotMessageType.DOCUMENT, message.document.fileId)
            else -> throw RuntimeException("un support type: $message")
        }
    }


    @Transactional
    open fun handleOperatorCommands(text: String, user: User): Boolean {
        when (getMsgKeyByValue(text, user)) {
            "STOP_CHAT" -> stopChatAndSearchUser(user)
            "NEXT_USER" -> nextUser(user)
            "SHORT_BREAK" -> breakOperator(user)
            "CONTINUE_WORK" -> continueWork(user)
            "END_WORK" -> endWork(user)
            "START_WORK" -> startWork(user)
            "TO_ANOTHER_OPERATOR" -> toAnotherOperator(user)
            else -> return handleCommonCommands(text, user)
        }
        return true
    }

    @Transactional
    open fun handleCommonCommands(text: String, user: User): Boolean {
        return when (text.lowercase()) {
            "/setlang" -> {
                sendChooseLangMsg(user)
                true
            }

            "/setname" -> {
                sendEnterYourFullName(user)
                true
            }

            else -> false
        }
    }


    open fun findActiveOperator(language: String): User? {
        return userRepository.findFirstByRoleAndOperatorStatusAndDeletedFalseOrderByModifiedDateAsc(
            UserRole.OPERATOR,
            OperatorStatus.ACTIVE
        )
    }

    @Synchronized
    open fun getQueuedSession(operator: User): QueueResponse? {
        val languages = operator.languages
        val languageToQueueMap = mapOf(
            LanguageEnum.UZ to queueUz,
            LanguageEnum.RU to queueRu,
            LanguageEnum.EN to queueEn
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
        }?.let { sessionId ->
            smallestQueue?.remove(sessionId)?.let { messages ->
                QueueResponse(sessionId, messages)
            }
        }
    }


    @Transactional
    open fun stopChat(operator: User) {
        val session = sessionRepository.findByOperatorIdAndStatus(operator.id, SessionStatusEnum.BUSY)
        session?.let {
            val user = it.user

            it.status = SessionStatusEnum.CLOSED
            operator.operatorStatus = OperatorStatus.ACTIVE
            it.operator = null
            userRepository.save(operator)
            user.state = UserStateEnum.ACTIVE_USER
            sessionRepository.save(it)
            userRepository.save(user)

            sendChatStoppedMsg(operator)
            sendRateMsg(user, operator, session)
            sendAskYourQuestionMsg(user)
        }
    }

    @Transactional
    open fun stopChatAndSearchUser(operator: User) {
        stopChat(operator)
        sendSearchingUserMsg(operator)
    }

    @Transactional
    open fun breakOperator(operator: User) {
        stopChat(operator)
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.PAUSED
            userRepository.save(operator)

            sendWorkPausedMsg(operator)
        }
    }

    open fun continueWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)

            sendWorkContinuedMsg(operator)
            sendSearchingUserMsg(operator)
        }
    }

    open fun startWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.ACTIVE
            userRepository.save(operator)

            sendWorkStartedMsg(operator)
            sendSearchingUserMsg(operator)
        }
    }

    open fun endWork(operator: User) {
        if (operator.operatorStatus != OperatorStatus.BUSY) {
            operator.operatorStatus = OperatorStatus.INACTIVE
            userRepository.save(operator)

            sendWorkEndedMsg(operator)
        }
    }

    private fun saveDoubleOperatorIfNeeded(operator: User, session: Session) {
        if (!doubleOperatorRepository.existsByOperatorIdAndSessionId(operator.id, session.id!!)) {
            doubleOperatorRepository.save(DoubleOperator(operator, session))
        }
    }

    private fun updateOperatorStatus(operator: User, status: OperatorStatus) {
        operator.operatorStatus = status
        userRepository.save(operator)
    }

    private fun resetSession(session: Session) {
        session.status = SessionStatusEnum.WAITING
        session.operator = null
        sessionRepository.save(session)
    }

    private fun prepareMessageText(message: Messages, session: Session, userLang: String): Messages {
        val prefix = if (message.user.isOperator())
            "${getMsg("OPERATOR", session.user)}:\n"
        else "${getMsg("USER", session.user)}:\n"

        message.text = message.text?.let { prefix + it }
        message.caption = message.caption?.let { prefix + it }

        addMessageToMap(session.id!!, message, userLang)
        return message
    }

    @Synchronized
    open fun toAnotherOperator(operator: User) {
        sessionRepository.findLastSessionByOperatorId(operator.id)?.let { session ->
            saveDoubleOperatorIfNeeded(operator, session)
            updateOperatorStatus(operator, OperatorStatus.ACTIVE)
            resetSession(session)

            val messages = botMessageRepository.findAllBySessionIdOrderByCreatedDateAsc(session.id!!)
            val userLang = session.user.languages.elementAt(0).toString()

            messages.forEach { message ->
                prepareMessageText(message, session, userLang)
            }

            sendSearchingUserMsg(operator)
        }
    }

    open fun sendAskYourQuestionMsg(user: User) {
        val sendMessage = SendMessage(user.id.toString(), getMsg("ASK_YOUR_QUESTION", user).htmlBold())
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        sendMessage.parseMode = ParseMode.HTML
        user.state = UserStateEnum.ASK_YOUR_QUESTION
        userRepository.save(user)
        this.execute(sendMessage)
    }

    open fun sendWrongNumberMsg(user: User) {
        this.execute(SendMessage(user.id.toString(), getMsg("WRONG_NUMBER", user)))
    }

    open fun sendRateMsg(user: User, operator: User, session: Session) {
        val sendMessage = SendMessage(
            user.id.toString(),
            getMsg("OPERATOR_STOPPED_CHAT", operator) + "\n" +
                    getMsg("PLEASE_RATE_OPERATOR_WORK", operator)
        )
        val btn1 = InlineKeyboardButton(getMsg("VERY_BAD", user))
        btn1.callbackData = "rateS1" + session.id
        val btn2 = InlineKeyboardButton(getMsg("BAD", user))
        btn2.callbackData = "rateS2" + session.id
        val btn3 = InlineKeyboardButton(getMsg("SATISFACTORY", user))
        btn3.callbackData = "rateS3" + session.id
        val btn4 = InlineKeyboardButton(getMsg("GOOD", user))
        btn4.callbackData = "rateS4" + session.id
        val btn5 = InlineKeyboardButton(getMsg("EXCELLENT", user))
        btn5.callbackData = "rateS5" + session.id
        val markup = InlineKeyboardMarkup(listOf(listOf(btn1), listOf(btn2), listOf(btn3), listOf(btn4), listOf(btn5)))
        sendMessage.replyMarkup = markup
        this.execute(sendMessage)
    }

    open fun sendChatStoppedMsg(operator: User) {
        val sendMessage = SendMessage(operator.id.toString(), getMsg("CHAT_STOPPED", operator).htmlBold())
        sendMessage.parseMode = ParseMode.HTML
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        this.execute(sendMessage)
    }

    open fun sendWorkPausedMsg(operator: User) {
        val sendMessage = SendMessage(operator.id.toString(), getMsg("WORK_PAUSED", operator).htmlBold())
        val row1 = KeyboardRow(2)
        row1.add(KeyboardButton(getMsg("CONTINUE_WORK", operator)))
        row1.add(KeyboardButton(getMsg("END_WORK", operator)))
        val markup = ReplyKeyboardMarkup(listOf(row1))
        markup.resizeKeyboard = true
        sendMessage.replyMarkup = markup
        sendMessage.parseMode = ParseMode.HTML
        this.execute(sendMessage)
    }

    open fun sendWorkEndedMsg(operator: User) {
        val sendMessage = SendMessage(operator.id.toString(), getMsg("WORK_ENDED", operator))
        val row1 = KeyboardRow(1)
        row1.add(KeyboardButton(getMsg("START_WORK", operator)))
        val markup = ReplyKeyboardMarkup(listOf(row1))
        markup.resizeKeyboard = true
        sendMessage.replyMarkup = markup
        this.execute(sendMessage)
    }

    open fun sendWorkStartedMsg(operator: User) {
        val sendMessage = SendMessage(operator.id.toString(), getMsg("WORK_STARTED", operator))
        val row1 = KeyboardRow(1)
        row1.add(KeyboardButton(getMsg("START_WORK", operator)))
        val markup = ReplyKeyboardMarkup(listOf(row1))
        markup.resizeKeyboard = true
        sendMessage.replyMarkup = markup
        this.execute(sendMessage)
    }

    open fun sendWorkContinuedMsg(operator: User) {
        this.execute(SendMessage(operator.id.toString(), getMsg("WORK_CONTINUED", operator)))
    }

    open fun sendSearchingUserMsg(operator: User) {
        val sendMessage = SendMessage(operator.id.toString(), getMsg("SEARCHING_USER", operator).htmlBold())
        sendMessage.parseMode = ParseMode.HTML
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        this.execute(sendMessage)
    }

    open fun getMsg(key: String, user: User): String {
        try {
            val locale = Locale.forLanguageTag(user.languages.elementAt(0).name.lowercase())
            return messageSource.getMessage(key, null, locale)
        } catch (e: Exception) {
            return "Error"
        }
    }

    open fun getMsgByLang(key: String, languageEnum: LanguageEnum): String {
        try {
            val locale = Locale.forLanguageTag(languageEnum.name.lowercase())
            return messageSource.getMessage(key, null, locale)
        } catch (e: Exception) {
            return "Error"
        }
    }

    open fun getMsgKeyByValue(value: String, user: User): String {
        for (language in user.languages) {
            val locale = Locale.forLanguageTag(language.name.lowercase())
            val bundle = ResourceBundle.getBundle("messages", locale)
            for (key in bundle.keySet())
                if (bundle.getString(key) == value)
                    return key
        }
        return ""
    }

    open fun sendChooseLangMsg(user: User) {
        val sendMessage = if (user.isOperator()) {
            val translatedTextChooseLanguage = getMsg("CHOOSE_LANGUAGE", user)
            val sendMessage = SendMessage(user.id.toString(), translatedTextChooseLanguage)
            sendMessage.replyMarkup = getChooseLanguageReplyMarkup(user)
            sendMessage

        } else {
            val sendMessage = SendMessage(user.id.toString(), "Choose language")
            val btn1 = InlineKeyboardButton("🇺🇸")
            btn1.callbackData = "setLangEN"
            val btn2 = InlineKeyboardButton("🇷🇺")
            btn2.callbackData = "setLangRU"
            val btn3 = InlineKeyboardButton("🇺🇿")
            btn3.callbackData = "setLangUZ"
            val markup = InlineKeyboardMarkup(listOf(listOf(btn1), listOf(btn2), listOf(btn3)))
            sendMessage.replyMarkup = markup
            sendMessage
        }
        val msgId = this.execute(sendMessage).messageId
        user.msgIdChooseLanguage = msgId
        user.state = UserStateEnum.CHOOSE_LANG
        userRepository.save(user)
    }

    open fun sendSharePhoneMsg(user: User) {
        val sendMessage = SendMessage(user.id.toString(), getMsg("CLICK_TO_SEND_YOUR_PHONE", user))
        val keyboardButton = KeyboardButton(getMsg("SHARE_PHONE_NUMBER", user))
        keyboardButton.requestContact = true
        val row = KeyboardRow(1)
        row.add(keyboardButton)
        val markup = ReplyKeyboardMarkup(listOf(row))
        markup.resizeKeyboard = true
        sendMessage.replyMarkup = markup
        this.execute(sendMessage)
        user.state = UserStateEnum.SEND_PHONE_NUMBER
        userRepository.save(user)
    }

    open fun sendEnterYourFullName(user: User) {
        val sendMessage = SendMessage(user.id.toString(), getMsg("SEND_YOUR_FULL_NAME", user))
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        this.execute(sendMessage)
        user.state = UserStateEnum.SEND_FULL_NAME
        userRepository.save(user)
    }

    open fun getChooseLanguageReplyMarkup(user: User): InlineKeyboardMarkup {
        val btn1 = InlineKeyboardButton("🇺🇸 ${getStatusEmojiByBoolean(user.languages.contains(LanguageEnum.EN))}")
        btn1.callbackData = "setLangEN"
        val btn2 = InlineKeyboardButton("🇷🇺 ${getStatusEmojiByBoolean(user.languages.contains(LanguageEnum.RU))}")
        btn2.callbackData = "setLangRU"
        val btn3 = InlineKeyboardButton("🇺🇿 ${getStatusEmojiByBoolean(user.languages.contains(LanguageEnum.UZ))}")
        btn3.callbackData = "setLangUZ"
        val markup = InlineKeyboardMarkup(listOf(listOf(btn1), listOf(btn2), listOf(btn3)))
        return markup
    }

    open fun getStatusEmojiByBoolean(t: Boolean): String {
        return if (t) "✅" else "❌"
    }

    @Transactional
    open fun nextUser(operator: User) {
        stopChat(operator)
        sendSearchingUserMsg(operator)
        contactActiveOperator(operator)
    }
}