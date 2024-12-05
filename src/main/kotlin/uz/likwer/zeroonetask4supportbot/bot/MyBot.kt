package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendAudio
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import com.pengrad.telegrambot.request.SendVideo
import com.pengrad.telegrambot.request.SendVoice
import lombok.RequiredArgsConstructor
import okhttp3.internal.wait
import uz.likwer.zeroonetask4supportbot.component.SpringContext
import uz.likwer.zeroonetask4supportbot.service.BotService

@RequiredArgsConstructor
class MyBot(val bot: TelegramBot, val botService: BotService) {

    companion object

    fun bot(): TelegramBot {
        return SpringContext.getBean(TelegramBot::class.java)
    }

    fun start() {
        bot.setUpdatesListener({ updates: List<Update> ->
            for (update in updates) {
                handleUpdate(update)
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
                val chatId = tgUser.id()

                if (message.text() != null) {
                    val text = message.text()

                    if (text.equals("/start")) {
                        botService.sendChooseLangMsg(chatId)
                    }
                } else if (message.voice() != null) {
                    val voice = message.voice()
                    val fileId = voice.fileId()

                    //TODO send to operator
                    bot.execute(SendVoice(chatId, fileId))
                } else if (message.audio() != null) {
                    val audio = message.audio()
                    val fileId = audio.fileId

                    //TODO send to operator
                    bot.execute(SendAudio(chatId, fileId))
                } else if (message.photo() != null) {
                    val photos = message.photo()

                    photos.sortByDescending { it.fileSize() }
                    for ((index, photo) in photos.withIndex()) {
                        if (index == photos.size / 4) {
                            val fileId = photo.fileId()

                            //TODO send to operator
                            // WAIT RESPONSE!!!
                            bot.execute(SendPhoto(chatId, fileId)).wait()
                        }
                    }
                } else if (message.video() != null) {
                    val video = message.video()
                    val fileId = video.fileId()

                    //TODO send to operator
                    // WAIT RESPONSE!!!
                    bot.execute(SendVideo(chatId, fileId)).wait()
                }



                if (message.caption() != null) {
                    val caption = message.caption()

                    //TODO send to operator
                    bot.execute(SendMessage(chatId, caption))
                }
            } else if (update.callbackQuery() != null) {
                val callbackQuery = update.callbackQuery()
                val tgUser = callbackQuery.from()
                val chatId = tgUser.id()
                val data = callbackQuery.data()

                if (data.startsWith("setLang")) {
                    val lang = data.substring("setLang".length)

                    botService.askPhone(chatId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}