package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import lombok.RequiredArgsConstructor
import uz.likwer.zeroonetask4supportbot.component.SpringContext
import uz.likwer.zeroonetask4supportbot.enums.MessageEffects
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