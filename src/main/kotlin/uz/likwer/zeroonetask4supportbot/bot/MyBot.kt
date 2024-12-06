package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.request.SendChatAction
import com.pengrad.telegrambot.request.SendContact
import com.pengrad.telegrambot.request.SendSticker
import com.pengrad.telegrambot.request.SendVideo
import lombok.RequiredArgsConstructor
import uz.likwer.zeroonetask4supportbot.bot.Utils.Companion.clearPhone
import uz.likwer.zeroonetask4supportbot.service.BotService
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RequiredArgsConstructor
class MyBot(
    private val bot: TelegramBot,
    private val botService: BotService,
    private val executorService: Executor = Executors.newFixedThreadPool(20)
) {

//    companion object { }

    fun start() {
        bot.setUpdatesListener({ updates: List<Update> ->
            for (update in updates) {
                executorService.execute {
                    handleUpdate(update)
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }, { e: TelegramException ->
            if (e.response() != null) {
                e.response().errorCode()
                e.response().description()
            }
        })
    }

    private fun handleUpdate(update: Update) {
        try {
            if (update.message() != null) {
                val message = update.message()
                val tgUser = message.from()
                //TODO user from entity package
//                val user = botService.getUser(tgUser)
                val chatId = tgUser.id()

                bot.execute(SendChatAction(chatId, ChatAction.typing))

                if (message.text() != null) {
                    val text = message.text()

                    if (text.equals("/start")) {
                        botService.sendChooseLangMsg(chatId)
                    } else {
//                        if (user.status == UserStatus.SEND_FULL_NAME) {
//                            user.fullName = text
//                        }
                    }
                } else if (message.contact() != null) {
                    val contact = message.contact()
                    val phoneNumber = contact.phoneNumber().clearPhone()

                    //TODO
//                    if (user.status == UserStatus.SEND_PHONE_NUMBER) {
//                        user.phone = phoneNumber
//                        bot.execute(SendMessage(chatId, "Send your fullname"))
//                        user.status = UserStatus.SEND_FULL_NAME
//                    } else if (user.status == UserStatus.BUSY) {
//                        botService.sendContactToOperator(user, contact, phoneNumber)
//                    }
                } else if (message.voice() != null) {
                    val voice = message.voice()
                    val fileId = voice.fileId()

                    //TODO
//                    if (user.status == UserStatus.BUSY) {
//                        botService.sendVoiceToOperator(user, voice)
//                    }
                } else if (message.audio() != null) {
                    val audio = message.audio()
                    val fileId = audio.fileId

                    //TODO
//                    if (user.status == UserStatus.BUSY) {
//                        botService.sendAudioToOperator(user, audio)
//                    }
                } else if (message.photo() != null) {
                    val photos = message.photo()

                    photos.sortByDescending { it.fileSize() }
                    val photo = photos[photos.size / 4]
                    val fileId = photo.fileId()

//                    if (user.status == UserStatus.BUSY) {
//                        if (message.caption() != null) {
//                            botService.sendPhotoWithCaptionToOperator(user, photo, message.caption())
//                        } else {
//                            botService.sendPhotoToOperator(user, photo)
//                        }
//                    }
                } else if (message.video() != null) {
                    val video = message.video()
                    val fileId = video.fileId()

                    //TODO if (user is busy) send to operator
                    bot.execute(SendVideo(chatId, video.fileId()))
                }
            } else if (update.callbackQuery() != null) {
                val callbackQuery = update.callbackQuery()
                val tgUser = callbackQuery.from()
                val chatId = tgUser.id()
                val data = callbackQuery.data()

                if (data.startsWith("setLang")) {
                    //TODO set user's lang
                    val lang = data.substring("setLang".length) // lang = "RU","EN","UZ"

                    botService.askPhone(chatId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}