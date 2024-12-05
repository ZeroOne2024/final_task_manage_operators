package uz.likwer.zeroonetask4supportbot.service

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.SendMessage
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.bot.Utils

@Service
class BotService(private val utils: Utils) {
    fun sendChooseLangMsg(chatId: Long) {

        utils.telegramBot().execute(
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
        utils.telegramBot().execute(SendMessage(chatId, "phone"))
    }

    fun getUser(tgUser: com.pengrad.telegrambot.model.User) {
        //TODO check user to exist
        
    }
}