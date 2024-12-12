package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.MessageEntity
import com.pengrad.telegrambot.model.request.*
import com.pengrad.telegrambot.request.*
import jakarta.transaction.Transactional
import org.springframework.scheduling.annotation.Scheduled

import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.backend.*
import java.util.concurrent.CopyOnWriteArrayList


@Service
class BotService(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val botTools: BotTools,
) {

    @Synchronized
    fun getUser(tgUser: com.pengrad.telegrambot.model.User): User {
        val userOpt = userRepository.findById(tgUser.id())
        if (userOpt.isPresent)
            return userOpt.get()
        var username = tgUser.username()
        if (username == null) username = ""
        var lastName = tgUser.lastName()
        lastName = if (lastName == null) "" else " $lastName"
        return userRepository.save(
            User(
                tgUser.id(),
                username,
                tgUser.firstName() + lastName,
                "",
            )
        )
    }

    fun bot(): TelegramBot {
        return Utils.telegramBot()
    }

    fun sendChooseLangMsg(user: User) {
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

    fun askPhone(user: User) {
        bot().execute(
            SendMessage(user.id, botTools.getMsg("CLICK_TO_SEND_YOUR_PHONE", user))
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton(botTools.getMsg("SHARE_PHONE_NUMBER", user)).requestContact(true)
                    ).resizeKeyboard(true)
                )
        )
        user.state = UserState.SEND_PHONE_NUMBER
        userRepository.save(user)
    }

    fun sendMessageToUser(user: User, message: Messages, session: Session) {
        val replyMessageId = message.replyMessageId?.let { replyId ->
            messageRepository.findBySessionIdAndMessageBotId(session.id!!, replyId)?.messageId
                ?: messageRepository.findBySessionIdAndMessageId(session.id!!, replyId)?.messageBotId
        }

        val chatId = user.id.toString()
        val response: com.pengrad.telegrambot.model.Message? = when (message.messageType) {
            MessageType.TEXT -> {
                val sendMessage = SendMessage(chatId, message.text ?: "")
                replyMessageId?.let { sendMessage.replyToMessageId(it) }
                bot().execute(sendMessage).message()
            }

            MessageType.PHOTO -> {
                val sendPhoto = SendPhoto(chatId, message.fileId ?: "")
                if (!message.caption.isNullOrEmpty()) sendPhoto.caption(message.caption)
                replyMessageId?.let { sendPhoto.replyToMessageId(it) }
                bot().execute(sendPhoto).message()
            }

            MessageType.VIDEO -> {
                val sendVideo = SendVideo(chatId, message.fileId ?: "")
                if (!message.caption.isNullOrEmpty()) sendVideo.caption(message.caption)
                replyMessageId?.let { sendVideo.replyToMessageId(it) }
                bot().execute(sendVideo).message()
            }

            MessageType.VOICE -> {
                val sendVoice = SendVoice(chatId, message.fileId ?: "")
                replyMessageId?.let { sendVoice.replyToMessageId(it) }
                bot().execute(sendVoice).message()
            }

            MessageType.AUDIO -> {
                val sendAudio = SendAudio(chatId, message.fileId ?: "")
                replyMessageId?.let { sendAudio.replyToMessageId(it) }
                bot().execute(sendAudio).message()
            }

            MessageType.CONTACT -> {
                val contact = message.contact!!
//                    ?: throw IllegalArgumentException("Contact information is missing")
                val sendContact = SendContact(chatId, contact.phone, contact.name)
                replyMessageId?.let { sendContact.replyToMessageId(it) }
                bot().execute(sendContact).message()
            }

            MessageType.LOCATION -> {
                val location = message.location!!
//                    ?: throw IllegalArgumentException("Location information is missing")
                val sendLocation = SendLocation(chatId, location.latitude, location.longitude)
                replyMessageId?.let { sendLocation.replyToMessageId(it) }
                bot().execute(sendLocation).message()
            }

            MessageType.STICKER -> {
                val sendSticker = SendSticker(chatId, message.fileId ?: "")
                replyMessageId?.let { sendSticker.replyToMessageId(it) }
                bot().execute(sendSticker).message()
            }

            MessageType.ANIMATION -> {
                val sendAnimation =
                    SendAnimation(chatId, message.fileId ?: "")
                if (!message.caption.isNullOrEmpty()) sendAnimation.caption(message.caption)
                replyMessageId?.let { sendAnimation.replyToMessageId(it) }
                bot().execute(sendAnimation).message()
            }

            MessageType.DOCUMENT -> {
                val sendDocument = SendDocument(chatId, message.fileId ?: "")
                if (!message.caption.isNullOrEmpty()) sendDocument.caption(message.caption)
                replyMessageId?.let { sendDocument.replyToMessageId(it) }
                bot().execute(sendDocument).message()
            }

            MessageType.DICE -> {
                val sendDice = SendDice(chatId)
                message.dice?.emoji?.let { sendDice.emoji(it) }
                replyMessageId?.let { sendDice.replyToMessageId(it) }
                bot().execute(sendDice).message()
            }

            else -> {
                println("Unsupported message type: ${message.messageType}")
                null
            }
        }

        response?.let {
            message.messageBotId = it.messageId()
        }

        message.deleted = true
        messageRepository.save(message)
    }

    fun editMessage(chatId: Long, messageId: Int, newText: String?, newCaption: String?) {
        // Retrieve the message from the database
        val message = messageRepository.findByUserIdAndMessageId(chatId,messageId)
            ?: throw IllegalArgumentException("Message with ID $messageId not found")

        if (message.messageBotId != null) {
            if (!newText.isNullOrBlank() && message.messageType == MessageType.TEXT) {
                message.text = newText

                if (message.session.user.id == chatId) {
                    val editMessage = EditMessageText(message.session.operator?.id, message.messageBotId!!, newText)
                    bot().execute(editMessage)
                } else {
                    val editMessage = EditMessageText(message.session.user.id, message.messageBotId!!, newText)
                    bot().execute(editMessage)
                }
            }

            if (!newCaption.isNullOrBlank() && message.messageType in listOf(
                    MessageType.PHOTO,
                    MessageType.VIDEO,
                    MessageType.DOCUMENT,
                    MessageType.ANIMATION
                )
            ) {
                message.caption = newCaption
                if (message.session.user.id == chatId) {
                    val editMessage =
                        EditMessageCaption(message.session.operator?.id, message.messageBotId!!).caption(newCaption)
                    bot().execute(editMessage)
                } else {
                    val editMessage =
                        EditMessageCaption(message.session.user.id, message.messageBotId!!).caption(newCaption)
                    bot().execute(editMessage)
                }
            }
        }else{
            if (!newText.isNullOrBlank() && message.messageType == MessageType.TEXT) {
                message.text = newText
            }
            if (!newCaption.isNullOrBlank() && message.messageType in listOf(
                    MessageType.PHOTO,
                    MessageType.VIDEO,
                    MessageType.DOCUMENT,
                    MessageType.ANIMATION
                )
            ){
             message.caption = newCaption
            }
        }

        messageRepository.save(message)
    }

    @Synchronized
    fun addMessageToMap(id: Long, message: Messages, language: String) {
        val targetQueue = when (language.lowercase()) {
            "en" -> DataLoader.queueEn
            "uz" -> DataLoader.queueUz
            "ru" -> DataLoader.queueRu
            else -> throw IllegalArgumentException("Unsupported language: $language")
        }

        targetQueue.compute(id) { _, existingMessages ->
            val messagesList = existingMessages ?: CopyOnWriteArrayList()
            messagesList.add(message)
            messagesList
        }
    }

    @Transactional
    fun getSession(user: User): Session {
        val session = sessionRepository.findLastSessionByUserId(user.id)
        return if (session != null) {
            if (session.status == SessionStatus.CLOSED) {
                bot().execute(SendMessage(user.id, botTools.getMsg("THE_OPERATOR_WILL_ANSWER_YOU_SOON", user)))
                user.let { sessionRepository.save(Session(it)) }
            } else {
                session
            }
        } else {
            bot().execute(SendMessage(user.id, botTools.getMsg("THE_OPERATOR_WILL_ANSWER_YOU_SOON", user)))
            user.let { sessionRepository.save(Session(it)) }
        }
    }

    fun getOperatorSession(operatorId: Long): Session? {
        return sessionRepository.findLastSessionByOperatorId(operatorId)?.let {
            if (it.status == SessionStatus.CLOSED) null
            it
        }
    }

    @Transactional
    fun setRate(sessionId: Long, rate: Short): Session? {
        return sessionRepository.findByIdAndDeletedFalse(sessionId)?.let {
            if (it.status == SessionStatus.CLOSED) {
                it.rate = rate
                sessionRepository.save(it)
            }
            it
        }
    }


    @Scheduled(fixedDelay = 5_000)
    fun contactActiveOperatorScheduled() {
        val activeOperators = userRepository.findFirstActiveOperator(UserRole.OPERATOR, OperatorStatus.ACTIVE)

        for (activeOperator in activeOperators) {
            val queuedSession = botTools.getQueuedSession(activeOperator)
            if (queuedSession != null) {
                var session = sessionRepository.findByIdAndDeletedFalse(queuedSession.sessionId)
                if (session != null) {

                    session.operator = activeOperator
                    session.status = SessionStatus.BUSY
                    session = sessionRepository.save(session)

                    activeOperator.operatorStatus = OperatorStatus.BUSY
                    val saved = userRepository.save(activeOperator)

                    bot().execute(
                        SendMessage(activeOperator.id, botTools.getMsg("USER", activeOperator)+": " + session.user.fullName)
                            .entities(
                                MessageEntity(
                                    MessageEntity.Type.text_mention,
                                    (botTools.getMsg("USER", activeOperator)+": ").length,
                                    session.user.fullName.length
                                )
                                    .user(com.pengrad.telegrambot.model.User(session.user.id))
                            ).replyMarkup(
                                ReplyKeyboardMarkup(
                                    KeyboardButton(botTools.getMsg("STOP_CHAT", activeOperator)),
                                    KeyboardButton(botTools.getMsg("NEXT_USER", activeOperator)),
                                    KeyboardButton(botTools.getMsg("SHORT_BREAK", activeOperator)),
//                                                    KeyboardButton("To another operator 📁")
                                ).resizeKeyboard(true)
                            )
                    )
                    for (message in queuedSession.messages) {
                        sendMessageToUser(saved, message, session)
                    }
                }
            }
        }
    }
}