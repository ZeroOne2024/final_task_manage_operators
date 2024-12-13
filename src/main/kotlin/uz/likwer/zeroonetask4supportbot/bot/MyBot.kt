package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
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
    private val diceRepository: DiceRepository,
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
            update.message()?.let { message ->
                val user = botService.getUser(message.from())
                val chatId = user.id

                bot.execute(SendChatAction(chatId, ChatAction.typing))

                if (user.state != UserState.ACTIVE_USER) {
                    if (message.text() != null) {
                        val text = message.text()

                        if (text.equals("/start")) {
                            if (user.languages.isEmpty())
                                botTools.sendChooseLangMsg(user)
                            else botTools.sendAskYourQuestionMsg(user)
                        } else {
                            if (user.state == UserState.SEND_FULL_NAME) {
                                user.fullName = text
                                user.state = UserState.ACTIVE_USER
                                userRepository.save(user)

                                botTools.sendAskYourQuestionMsg(user)
                            }
                        }

                    } else if (message.contact() != null) {
                        val contact = message.contact()
                        val phoneNumber = contact.phoneNumber().clearPhone()

                        if (user.state == UserState.SEND_PHONE_NUMBER) {
                            if (contact.userId() != chatId) {
                                botTools.sendWrongNumberMsg(user)
                            } else {
                                user.phoneNumber = phoneNumber
                                bot.execute(SendMessage(chatId, botTools.getMsg("SEND_YOUR_FULL_NAME", user)))
                                user.state = UserState.SEND_FULL_NAME
                                userRepository.save(user)

                                if (user.state == UserState.SEND_PHONE_NUMBER) {
                                    user.phoneNumber = phoneNumber
                                    bot.execute(SendMessage(chatId, botTools.getMsg("SEND_YOUR_FULL_NAME", user)))
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
                    val caption = message.caption()
                    val text = message.text()
                    val location = message.location()?.let {
                        locationRepository.save(Location(it.latitude(), it.longitude()))
                    }
                    val contact = message.contact()?.let {
                        contactRepository.save(Contact(it.firstName(), it.phoneNumber()))
                    }
                    val dice = message.dice()?.let {
                        diceRepository.save(Dice(it.value(), it.emoji()))
                    }


                    if (user.isOperator()) {
                        val isCommand = botTools.processCommand(text, user)

                        if (!isCommand) {
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
                                    contact = contact,
                                    dice = dice,
                                )
                                val savedMessage = messageRepository.save(newMessage)
                                botService.sendMessageToUser(session.user, savedMessage, session)
                            }
                        }
                    } else {
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
                                contact = contact,
                                dice = dice,
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
            }
            update.editedMessage()?.run {
                val chatId = this.from().id()
                val messageId = this.messageId()
                val newText = this.text()
                val newCaption = this.caption()

                botService.editMessage(chatId, messageId, newText, newCaption)
            }

            update.callbackQuery()?.let { callbackQuery ->
                val user = botService.getUser(callbackQuery.from())
                val chatId = user.id
                var data = callbackQuery.data()

                if (data.startsWith("setLang")) {
                    val lang = Language.valueOf(data.substring("setLang".length).uppercase())

                    if (user.state == UserState.CHOOSE_LANG) {
                        if (user.isOperator()) {

                        } else {
                            if (!user.languages.contains(lang)) {
                                user.languages = mutableListOf(lang)
                                bot.execute(DeleteMessage(chatId, callbackQuery.message().messageId()))
                            }
                        }
                    }

                    if (user.phoneNumber.isEmpty()) {
                        botTools.sendSharePhoneMsg(user)
                    }
                } else if (data.startsWith("rateS")) {
                    data = data.substring("rateS".length)
                    val rate = data.substring(0, 1).toShort()
                    val sessionId = data.substring(1).toLong()

                    botService.setRate(sessionId, rate)
                    bot.execute(
                        AnswerCallbackQuery(callbackQuery.id())
                            .text(botTools.getMsg("THANK_YOU", user))
                            .showAlert(true)
                    )
                    bot.execute(DeleteMessage(chatId, callbackQuery.message().messageId()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
