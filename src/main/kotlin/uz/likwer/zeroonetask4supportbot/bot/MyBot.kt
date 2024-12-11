package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.DeleteMessage
import com.pengrad.telegrambot.request.SendChatAction
import com.pengrad.telegrambot.request.SendMessage
import lombok.RequiredArgsConstructor
import uz.likwer.zeroonetask4supportbot.backend.*
import uz.likwer.zeroonetask4supportbot.bot.Utils.Companion.clearPhone
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RequiredArgsConstructor
class MyBot(
    private val bot: TelegramBot,
    private val botService: BotService,
    private val userRepository: UserRepository,
    private val botTools: BotTools,
    private val messageRepository: MessageRepository,
    private val locationRepository: LocationRepository,
    private val contactRepository: ContactRepository,
    private val executorService: Executor = Executors.newFixedThreadPool(20)
) {

//    companion object { }

    fun start() {
        bot.setUpdatesListener({ updates: List<Update> ->
            for (update in updates) {
                executorService.execute {
                    handleUpdate(update)
                }
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
                val user = botService.getUser(tgUser)
                val chatId = tgUser.id()

                bot.execute(SendChatAction(chatId, ChatAction.typing))
                if (user.state != UserState.ACTIVE_USER) {
                    if (message.text() != null) {
                        val text = message.text()

                        if (text.equals("/start")) {
                            botService.sendChooseLangMsg(user)
                        } else {
                            if (user.state == UserState.SEND_FULL_NAME) {
                                user.fullName = text
                                user.state = UserState.ACTIVE_USER
                                userRepository.save(user)

                                bot.execute(
                                    SendMessage(chatId, "Ask your question")
                                        .replyMarkup(ReplyKeyboardRemove())
                                )
                            }
                        }
                    } else if (message.contact() != null) {
                        val contact = message.contact()
                        val phoneNumber = contact.phoneNumber().clearPhone()

                        if (user.state == UserState.SEND_PHONE_NUMBER) {
                            if (contact.userId() != chatId) {
                                bot.execute(SendMessage(chatId, "wrong number"))
                            } else {
                                user.phoneNumber = phoneNumber
                                bot.execute(SendMessage(chatId, "Send your full name"))
                                user.state = UserState.SEND_FULL_NAME
                                userRepository.save(user)

                                if (user.state == UserState.SEND_PHONE_NUMBER) {
                                    user.phoneNumber = phoneNumber
                                    bot.execute(SendMessage(chatId, "Send your full name"))
                                    user.state = UserState.SEND_FULL_NAME
                                    userRepository.save(user)
                                }
                            }
                        }
                    }
                } else {

                    val messageId = message.messageId()
                    val messageReplyId = message.replyToMessage()?.messageId()
                    val typeAndFileId = botTools.determineMessageType(update.message())
                    val caption = message?.caption()
                    val text = message.text()
                    val location = message.location()?.let {
                        locationRepository.save(Location(it.latitude(), it.longitude()))
                    }
                    val contact = message.contact()?.let {
                        contactRepository.save(Contact(it.firstName(), it.phoneNumber()))
                    }

                    if (user.operatorStatus != null) {

                            if (text != null) {
                                if (text.equals("Stop chat ❌")) {
                                    botTools.stopChat(user)
                                    return
                                } else if (text.equals("Next user ➡️")) {
                                    botTools.nextUser(user)
                                    return
                                } else if (text.equals("Short break ▶️")) {
                                    botTools.breakOperator(user)
                                    return
                                } else if (text.equals("Continue work ⏸️")) {
                                    botTools.continueWork(user)
                                    return
                                } else if (text.equals("End work 🏠")) {
                                    botTools.endWork(user)
                                    return
                                }
                            }
                            val session = botService.getOperatorSession(chatId)
                            session?.let {
                                val newMessage = Messages(
                                    user = session.operator!!,
                                    session = session,
                                    messageId = messageId,
                                    replyMessageId = messageReplyId,
                                    messageType = typeAndFileId.first,
                                    text = text,
                                    caption = caption,
                                    fileId = typeAndFileId.second,
                                    location = location,
                                    contact = contact
                                )
                                val savedMessage = messageRepository.save(newMessage)
                                botService.sendMessageToUser(session.user, savedMessage, session)
                            }
                        }
                    else {
                        val sessionOpt = botService.getSession(user)
                        sessionOpt.let { session ->
                            val newMessage = Messages(
                                user = session.user,
                                session = session,
                                messageId = messageId,
                                replyMessageId = messageReplyId,
                                messageType = typeAndFileId.first,
                                text = text,
                                caption = caption,
                                fileId = typeAndFileId.second,
                                location = location,
                                contact = contact
                            )
                            val savedMessage = messageRepository.save(newMessage)

                            if (session.operator != null) {
                                botService.sendMessageToUser(session.operator!!, savedMessage, session)
                            } else {
                                    botService.addMessageToMap(
                                        session.id!!,
                                        savedMessage,
                                        session.user.languages[0].toString()
                                    )

                            }
                        }
                    }
                }
            } else if (update.callbackQuery() != null) {
                val callbackQuery = update.callbackQuery()
                val tgUser = callbackQuery.from()
                val user = botService.getUser(tgUser)
                val chatId = tgUser.id()
                var data = callbackQuery.data()

                if (data.startsWith("setLang")) {
                    val lang = Language.valueOf(data.substring("setLang".length).uppercase())
                    if (!user.languages.contains(lang)) {
                        user.languages.add(lang)
                    }

                    if (user.phoneNumber.isEmpty()) {
                        botService.askPhone(user)
                    }
                } else if (data.startsWith("rateS")) {
                    data = data.substring("rateS".length)
                    val rate = data.substring(0, 1).toShort()
                    val sessionId = data.substring(1).toLong()

                    botService.setRate(sessionId, rate)
                    bot.execute(AnswerCallbackQuery(callbackQuery.id()).text("Thank you❤️").showAlert(true))
                    bot.execute(DeleteMessage(chatId, callbackQuery.message().messageId()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
