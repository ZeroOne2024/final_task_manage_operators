package uz.likwer.zeroonetask4supportbot.bot.backend

import org.springframework.boot.CommandLineRunner
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import uz.likwer.zeroonetask4supportbot.bot.bot.BotService
import uz.likwer.zeroonetask4supportbot.bot.bot.SupportTelegramBot
import uz.likwer.zeroonetask4supportbot.bot.bot.SupportTelegramBot.Companion.activeBots
import java.util.concurrent.CopyOnWriteArrayList

@Component
class DataLoader(
    private val userRepository: UserRepository,
    private val botRepository: BotRepository,
    private val botMessageRepository: BotMessageRepository,
    private val locationRepository: LocationRepository,
    private val contactRepository: ContactRepository,
    private val diceRepository: DiceRepository,
    private val sessionRepository: SessionRepository,
    private val messageSource: MessageSource,
    private val doubleOperatorRepository: DoubleOperatorRepository,
    private val botService: BotService,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val bots = botRepository.findAllByStatus(BotStatusEnum.ACTIVE)
        for (bot in bots) {
            val supportTelegramBot = SupportTelegramBot(
                "",
                bot.token,
                1L,
                userRepository,
                botMessageRepository,
                locationRepository,
                contactRepository,
                diceRepository,
                sessionRepository,
                messageSource,
                doubleOperatorRepository
            )
            val me = supportTelegramBot.meAsync.get()
            supportTelegramBot.botId = bot.id!!
            supportTelegramBot.username = me.userName

            botService.registerBot(supportTelegramBot)
            activeBots[bot.token] = supportTelegramBot
        }
    }

    @Component
    class LoadMessagesToQueues(
        private val botMessageRepository: BotMessageRepository
    ) : CommandLineRunner {
        override fun run(vararg args: String?) {
            val groupedMessages = botMessageRepository.findMessagesGroupedBySessionId()

            val sessionMessagesMap = groupedMessages.groupBy {
                it["session"] as Session
            }.mapValues { entry ->
                entry.value.map { it["message"] as Messages }
            }

            sessionMessagesMap.forEach { (session, messages) ->
                for (bot in activeBots) {
                    if (bot.value.botId == session.botId) {
                        val firstMessage = messages.firstOrNull() ?: return@forEach
                        val language = firstMessage.user.languages.firstOrNull() ?: LanguageEnum.EN

                        val messagesList = CopyOnWriteArrayList(messages)

                        when (language) {
                            LanguageEnum.UZ -> bot.value.queueUz[session.id!!] = messagesList
                            LanguageEnum.EN -> bot.value.queueEn[session.id!!] = messagesList
                            LanguageEnum.RU -> bot.value.queueRu[session.id!!] = messagesList
                        }
                    }
                }
            }
        }
    }
}