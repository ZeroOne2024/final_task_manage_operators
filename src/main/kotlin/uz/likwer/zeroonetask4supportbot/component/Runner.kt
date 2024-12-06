package uz.likwer.zeroonetask4supportbot.component

import com.pengrad.telegrambot.TelegramBot
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import uz.likwer.zeroonetask4supportbot.backend.UserRepository
import uz.likwer.zeroonetask4supportbot.bot.BotService
import uz.likwer.zeroonetask4supportbot.bot.MyBot

@Component
class Runner(
    private val botService: BotService,
    private val userRepository: UserRepository
) : CommandLineRunner {

    // t.me/zero_one_support_bot
    private val botToken: String = "7261395309:AAHu4Eqm4oEpgQOEA-J2uXJLWCBXJBkt1vI"
    val telegramBot = TelegramBot(botToken)

    override fun run(vararg args: String?) {
        MyBot(telegramBot, botService, userRepository).start()
    }

    @Bean
    fun telegramBot(): TelegramBot {
        return telegramBot
    }
}