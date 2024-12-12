package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.LinkPreviewOptions
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
                val sendContact = SendContact(chatId, contact.phone, contact.name)
                replyMessageId?.let { sendContact.replyToMessageId(it) }
                bot().execute(sendContact).message()
            }

            MessageType.LOCATION -> {
                val location = message.location!!
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

    fun editMessage(
        chatId: Long,
        messageId: Int,
        entities: Array<MessageEntity>,
        newText: String? = null,
        newCaption: String? = null
    ) {
        val messageOpt = messageRepository.findByUserIdAndMessageId(chatId, messageId)
        messageOpt?.let { message ->
            val fileList = listOf(
                MessageType.PHOTO,
                MessageType.VIDEO,
                MessageType.DOCUMENT,
                MessageType.ANIMATION
            )

            if (message.messageBotId != null) {
                newText?.let { text ->
                    if (message.messageType == MessageType.TEXT) {
                        message.text = newText

                        val editMessage = if (message.session.user.id == chatId)
                            EditMessageText(message.session.operator!!.id, message.messageBotId!!, text)
                        else EditMessageText(message.session.user.id, message.messageBotId!!, text)
                        bot().execute(editMessage)
                    }
                }

                newCaption?.let { caption ->
                    if (message.messageType in fileList) {
                        message.caption = caption
                        val editMessage = if (message.session.user.id == chatId)
                            EditMessageCaption(message.session.operator?.id, message.messageBotId!!).caption(caption)
                        else EditMessageCaption(message.session.user.id, message.messageBotId!!).caption(caption)
                        bot().execute(editMessage)
                    }
                }
            } else {
                newText?.let { text ->
                    if (message.messageType == MessageType.TEXT)
                        message.text = text
                }
                newCaption?.let { caption ->
                    if (message.messageType in fileList)
                        message.caption = caption
                }
            }
            messageRepository.save(message)
        }
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


    private fun sendUserInfoForOperator(operator: User, user: User) {
        val replyKeyboardMarkup = ReplyKeyboardMarkup(
            KeyboardButton(botTools.getMsg("STOP_CHAT", operator))
        ).addRow(
            KeyboardButton(botTools.getMsg("NEXT_USER", operator)),
            KeyboardButton(botTools.getMsg("TO_ANOTHER_OPERATOR", operator))
        ).addRow(KeyboardButton(botTools.getMsg("SHORT_BREAK", operator)))
            .resizeKeyboard(true)
        val messageEntity = if (user.username.isEmpty())
            MessageEntity(
                MessageEntity.Type.text_mention,
                (botTools.getMsg("USER", operator) + ": ").length,
                user.fullName.length
            ).user(com.pengrad.telegrambot.model.User(user.id))
        else
            MessageEntity(
                MessageEntity.Type.text_link,
                (botTools.getMsg("USER", operator) + ": ").length,
                user.fullName.length
            ).url("t.me/" + user.username)
        bot().execute(
            SendMessage(operator.id, botTools.getMsg("USER", operator) + ": " + user.fullName)
                .entities(messageEntity)
                .linkPreviewOptions(LinkPreviewOptions().isDisabled(true))
                .replyMarkup(replyKeyboardMarkup)

        )
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

                    sendUserInfoForOperator(activeOperator, session.user)

                    for (message in queuedSession.messages) {
                        sendMessageToUser(saved, message, session)
                    }
                }
            }
        }
    }
}