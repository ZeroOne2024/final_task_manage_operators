package uz.likwer.zeroonetask4supportbot.component

import com.pengrad.telegrambot.TelegramBot
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import uz.likwer.zeroonetask4supportbot.backend.*
import uz.likwer.zeroonetask4supportbot.bot.BotService
import uz.likwer.zeroonetask4supportbot.bot.MyBot

@Component
class Runner(
    private val botService: BotService,
    private val userRepository: UserRepository,
    private val botTools: BotTools,
    private val messageRepository: MessageRepository,
    private val locationRepository: LocationRepository,
    private val contactRepository: ContactRepository,
    private val diceRepository: DiceRepository,
) : CommandLineRunner {

    // t.me/zero_one_support_bot
//    private val botToken: String = "7261395309:AAHu4Eqm4oEpgQOEA-J2uXJLWCBXJBkt1vI"
    private val botToken: String = "7848759727:AAEV8D5Gn1_28lly4GhhFyswgN5ySyDQZSk"
    val telegramBot = TelegramBot(botToken)

    override fun run(vararg args: String?) {
        MyBot(
            telegramBot,
            botService,
            userRepository,
            botTools,
            messageRepository,
            locationRepository,
            contactRepository,
            diceRepository
        ).start()
    }

    @Bean
    fun telegramBot(): TelegramBot {
        return telegramBot
    }
}