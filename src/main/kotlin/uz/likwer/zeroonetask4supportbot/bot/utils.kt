package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import uz.likwer.zeroonetask4supportbot.backend.BotTools
import uz.likwer.zeroonetask4supportbot.component.SpringContext

class Utils {
    companion object {
        private var bot: TelegramBot? = null
        fun telegramBot(): TelegramBot {
            if (bot == null)
                return SpringContext.getBean(TelegramBot::class.java)
            return bot as TelegramBot
        }

        private var botTools: BotTools? = null
        fun botTools(): BotTools {
            if (botTools == null)
                return SpringContext.getBean(BotTools::class.java)
            return botTools as BotTools
        }

        fun String.prettyPhoneNumber(): String {
            return try {
                var phone = this.trim()
                if (phone.startsWith("+"))
                    phone = phone.substring(1)
                val regex = "(\\d{3})(\\d{2})(\\d{3})(\\d{2})(\\d{2})".toRegex()
                phone.replace(regex, "+$1 $2 $3 $4 $5")
            } catch (e: Exception) {
                this
            }
        }

        fun String.clearPhone(): String {
            return try {
                this.replace(Regex("[^0-9]"), "")
            } catch (e: Exception) {
                this
            }
        }
    }
}