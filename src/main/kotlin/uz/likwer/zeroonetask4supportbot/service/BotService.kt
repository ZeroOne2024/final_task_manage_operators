package uz.likwer.zeroonetask4supportbot.service

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.*
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
import uz.likwer.zeroonetask4supportbot.bot.Utils

//TODO
//import uz.likwer.zeroonetask4supportbot.entity.User

@Service
class BotService {
    fun getUser(tgUser: com.pengrad.telegrambot.model.User) {
        //TODO search user by tgUser.id() if user not found create new user else return found user
//        return user
    }

    fun bot(): TelegramBot {
        return Utils.telegramBot()
    }

    fun sendChooseLangMsg(chatId: Long) {
        bot().execute(
            SendMessage(chatId, "Choose language")
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
                    )
                )
        )
    }

    //TODO   import user from entity package 👇
    fun sendPhotoWithCaptionToOperator(user: User, photo: PhotoSize, caption: String) {
//        if (user.status == UserStatus.BUSY) {
//            bot().execute(SendPhoto(operatorId, photo.fileId()).caption(caption))
//        }
    }

    fun sendPhotoToOperator(user: User, photo: PhotoSize) {
//        if (user.status == UserStatus.BUSY) {
//            bot().execute(SendPhoto(operatorId, photo.fileId()))
//        }
    }

    fun sendContactToOperator(user: User, contact: Contact, phoneNumber: String) {
//        if (user.status == UserStatus.BUSY) {
//            bot().execute(SendContact(operatorId, phoneNumber, contact.firstName()))
//        }
    }
    fun sendVoiceToOperator(user: User, voice: Voice) {
//        if (user.status == UserStatus.BUSY) {
//            bot().execute(SendVoice(operatorId, voice.fileId()))
//        }
    }
    fun sendAudioToOperator(user: User, audio: Audio) {
//        if (user.status == UserStatus.BUSY) {
//            bot().execute(SendAudio(operatorId, audio.fileId()))
//        }
    }
}