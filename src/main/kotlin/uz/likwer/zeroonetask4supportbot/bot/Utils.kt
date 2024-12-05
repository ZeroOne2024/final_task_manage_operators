package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import org.springframework.stereotype.Service
import uz.likwer.zeroonetask4supportbot.component.SpringContext

@Service
class Utils {
    fun telegramBot(): TelegramBot {
        return SpringContext.getBean(TelegramBot::class.java)
    }
}