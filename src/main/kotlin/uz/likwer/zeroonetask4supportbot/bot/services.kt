package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.Audio
import com.pengrad.telegrambot.model.Contact
import com.pengrad.telegrambot.model.PhotoSize
import com.pengrad.telegrambot.model.Voice
import com.pengrad.telegrambot.model.request.*
import com.pengrad.telegrambot.request.*
import jakarta.transaction.Transactional

import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.backend.*


@Service
class BotService(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,) {
    fun getUser(tgUser: com.pengrad.telegrambot.model.User): User {
        val userOpt = userRepository.findById(tgUser.id())
        if (userOpt.isPresent)
            return userOpt.get()
        return userRepository.save(
            User(
                tgUser.id(),
                tgUser.username(),
                tgUser.firstName() + " " + tgUser.lastName(),
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
    }

    fun askPhone(chatId: Long) {
        //TODO send translated msg to user's lang
        bot().execute(
            SendMessage(chatId, "Click 👇 to send share your phone")
                .replyMarkup(
                    ReplyKeyboardMarkup(
                        KeyboardButton("Share phone number").requestContact(true)
                    ).resizeKeyboard(true)
                )
        )
    }

//    fun sendPhotoWithCaptionToOperator(user: User, photo: PhotoSize, caption: String) {
//        if (user.state == UserState.TALKING) {
//            bot().execute(SendPhoto(user.talkingUserId, photo.fileId()).caption(caption))
//        }
//    }
//
//    fun sendPhotoToOperator(user: User, photo: PhotoSize) {
//        if (user.state == UserState.TALKING) {
//            bot().execute(SendPhoto(user.talkingUserId, photo.fileId()))
//        }
//    }
//
//    fun sendContactToOperator(user: User, contact: Contact, phoneNumber: String) {
//        if (user.state == UserState.TALKING) {
//            bot().execute(SendContact(user.talkingUserId, phoneNumber, contact.firstName()))
//        }
//    }
//
//    fun sendVoiceToOperator(user: User, voice: Voice) {
//        if (user.state == UserState.TALKING) {
//            bot().execute(SendVoice(user.talkingUserId, voice.fileId()))
//        }
//    }
//
//    fun sendAudioToOperator(user: User, audio: Audio) {
//        if (user.state == UserState.TALKING) {
//            bot().execute(SendAudio(user.talkingUserId, audio.fileId))
//        }
//    }

    fun sendMessageToUser(user: User, message: Messages) {
        // Handle reply message ID, if present
        val replyMessageId = message.replyMessageId?.let {
            messageRepository.findByUserIdAndMessageBotId(user.id, it)?.messageBotId
                ?: throw MessageNotFoundException()
        }

        val chatId = user.id.toString() // Assuming chat ID is the user ID
        val response: com.pengrad.telegrambot.model.Message? = when (message.messageType) {
            MessageType.TEXT -> {
                val sendMessage = SendMessage(chatId, message.text ?: "")
                replyMessageId?.let { sendMessage.replyToMessageId(it) }
                bot().execute(sendMessage).message()
            }
            MessageType.PHOTO -> {
                val sendPhoto = SendPhoto(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                if (!message.caption.isNullOrEmpty()) sendPhoto.caption(message.caption)
                replyMessageId?.let { sendPhoto.replyToMessageId(it) }
                bot().execute(sendPhoto).message()
            }
            MessageType.VIDEO -> {
                val sendVideo = SendVideo(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                if (!message.caption.isNullOrEmpty()) sendVideo.caption(message.caption)
                replyMessageId?.let { sendVideo.replyToMessageId(it) }
                bot().execute(sendVideo).message()
            }
            MessageType.VOICE -> {
                val sendVoice = SendVoice(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                replyMessageId?.let { sendVoice.replyToMessageId(it) }
                bot().execute(sendVoice).message()
            }
            MessageType.AUDIO -> {
                val sendAudio = SendAudio(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                replyMessageId?.let { sendAudio.replyToMessageId(it) }
                bot().execute(sendAudio).message()
            }
            MessageType.CONTACT -> {
                val contact = message.contact
                    ?: throw IllegalArgumentException("Contact information is missing")
                val sendContact = SendContact(chatId, contact.phone, contact.name)
                replyMessageId?.let { sendContact.replyToMessageId(it) }
                bot().execute(sendContact).message()
            }
            MessageType.LOCATION -> {
                val location = message.location
                    ?: throw IllegalArgumentException("Location information is missing")
                val sendLocation = SendLocation(chatId, location.latitude, location.longitude)
                replyMessageId?.let { sendLocation.replyToMessageId(it) }
                bot().execute(sendLocation).message()
            }
            MessageType.STICKER -> {
                val sendSticker = SendSticker(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                replyMessageId?.let { sendSticker.replyToMessageId(it) }
                bot().execute(sendSticker).message()
            }
            MessageType.ANIMATION -> {
                val sendAnimation = SendAnimation(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                if (!message.caption.isNullOrEmpty()) sendAnimation.caption(message.caption)
                replyMessageId?.let { sendAnimation.replyToMessageId(it) }
                bot().execute(sendAnimation).message()
            }
            MessageType.DOCUMENT -> {
                val sendDocument = SendDocument(chatId, message.fileId ?: "") // Assuming fileId is already on Telegram
                if (!message.caption.isNullOrEmpty()) sendDocument.caption(message.caption)
                replyMessageId?.let { sendDocument.replyToMessageId(it) }
                bot().execute(sendDocument).message()
            }
            else -> {
                println("Unsupported message type: ${message.messageType}")
                null
            }
        }

        // Save the bot message ID to the database
        response?.let {
            message.messageBotId = it.messageId()
        }

        // Mark the message as deleted (if applicable) and save it
        message.deleted = true
        messageRepository.save(message)
    }

    @Transactional
    fun getSession(userId: Long): Session {
        return sessionRepository.findLastSessionByUserId(userId)?.run {
            if (status == SessionStatus.CLOSED) {
                userRepository.findByIdAndDeletedFalse(userId)
                    ?.let { sessionRepository.save(Session(it)) }
                    ?: throw UserNotFoundException()
            } else {
                this
            }
        } ?: userRepository.findByIdAndDeletedFalse(userId)
            ?.let { sessionRepository.save(Session(it)) }
        ?: throw UserNotFoundException()
    }
    @Transactional
    fun terminateSession(operatorId: Long): Session {
        return sessionRepository.findLastSessionByOperatorId(operatorId)?.run {
            status=SessionStatus.CLOSED
            sessionRepository.save(this)
        }?:throw SessionNotFoundExistException()
    }

    @Transactional
    fun setBusy(sessionId: Long, operatorId: Long): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundExistException() }

        val setOperator = userRepository.findByIdAndDeletedFalse(operatorId)?:throw UserNotFoundException()

        return session.run {
            if (status != SessionStatus.WAITING) throw SessionAlreadyBusyException()

            status = SessionStatus.BUSY
            operator = setOperator
            sessionRepository.save(this)
        }
    }

    @Transactional
    fun setRate(userId: Long, rate: Short): Session {
        return sessionRepository.findLastSessionByUserId(userId)?.run {
            if (status != SessionStatus.BUSY) throw SessionClosedException()
            this.rate=rate
            sessionRepository.save(this)
        }?: throw SessionNotFoundExistException()
    }




}