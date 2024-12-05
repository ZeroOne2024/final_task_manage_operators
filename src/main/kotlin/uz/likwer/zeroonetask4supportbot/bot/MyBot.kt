package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import lombok.RequiredArgsConstructor
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
@RequiredArgsConstructor
class MyBot(private val telegramBot: TelegramBot) {

    @Bean
    fun telegramBot(): TelegramBot {
        return telegramBot
    }

    fun start() {
        telegramBot.setUpdatesListener({ updates: List<Update> ->
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

        }catch(e: Exception) {
            e.printStackTrace()
        }
    }
}