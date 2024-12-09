package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.Audio
import com.pengrad.telegrambot.model.Contact
import com.pengrad.telegrambot.model.PhotoSize
import com.pengrad.telegrambot.model.Voice

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.SendAudio
import com.pengrad.telegrambot.request.SendContact
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import com.pengrad.telegrambot.request.SendVoice
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.backend.*


@Service
class BotService(private val userRepository: UserRepository) {
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

    fun sendPhotoWithCaptionToOperator(user: User, photo: PhotoSize, caption: String) {
        if (user.state == UserState.TALKING) {
            bot().execute(SendPhoto(user.talkingUserId, photo.fileId()).caption(caption))
        }
    }

    fun sendPhotoToOperator(user: User, photo: PhotoSize) {
        if (user.state == UserState.TALKING) {
            bot().execute(SendPhoto(user.talkingUserId, photo.fileId()))
        }
    }

    fun sendContactToOperator(user: User, contact: Contact, phoneNumber: String) {
        if (user.state == UserState.TALKING) {
            bot().execute(SendContact(user.talkingUserId, phoneNumber, contact.firstName()))
        }
    }

    fun sendVoiceToOperator(user: User, voice: Voice) {
        if (user.state == UserState.TALKING) {
            bot().execute(SendVoice(user.talkingUserId, voice.fileId()))
        }
    }

    fun sendAudioToOperator(user: User, audio: Audio) {
        if (user.state == UserState.TALKING) {
            bot().execute(SendAudio(user.talkingUserId, audio.fileId))
        }
    }
    fun sendMessageToUser(user: User, message: Messages) {

        when (message.messageType) {
            MessageType.TEXT -> {
                bot().execute(SendMessage(user.id, message.text ?: ""))
            }
            MessageType.PHOTO -> {
                bot().execute(
                    SendPhoto(chatId, message.fileId ?: "").caption(message.caption ?: "")
                )
            }
            MessageType.VIDEO -> {
                bot().execute(
                    SendVideo(chatId, message.fileId ?: "").caption(message.caption ?: "")
                )
            }
            MessageType.VOICE -> {
                bot().execute(SendVoice(chatId, message.fileId ?: ""))
            }
            MessageType.AUDIO -> {
                bot().execute(SendAudio(chatId, message.fileId ?: ""))
            }
            MessageType.CONTACT -> {
                val contact = message.contact
                if (contact != null) {
                    bot().execute(SendContact(chatId, contact.phone, contact.name))
                }
            }
            MessageType.LOCATION -> {
                val location = message.location
                if (location != null) {
                    bot().execute(SendLocation(chatId, location.latitude, location.longitude))
                }
            }
            MessageType.STICKER -> {
                bot().execute(SendSticker(chatId, message.fileId ?: ""))
            }
            MessageType.ANIMATION -> {
                bot().execute(SendAnimation(chatId, message.fileId ?: ""))
            }
            MessageType.DOCUMENT -> {
                bot().execute(SendDocument(chatId, message.fileId ?: ""))
            }
            else -> {
                println("Unsupported message type: ${message.messageType}")
            }
        }
    }

}