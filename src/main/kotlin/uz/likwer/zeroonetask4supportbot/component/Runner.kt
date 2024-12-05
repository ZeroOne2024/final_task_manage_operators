package uz.likwer.zeroonetask4supportbot.component

import com.pengrad.telegrambot.TelegramBot
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import uz.likwer.zeroonetask4supportbot.bot.MyBot

@Component
class Runner : CommandLineRunner {
    private val botToken: String = ""

    override fun run(vararg args: String?) {
        MyBot(TelegramBot(botToken)).start()
    }
}