package uz.likwer.zeroonetask4supportbot.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.request.*
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
    private val sessionService: SessionService,
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

                                bot.execute(SendMessage(chatId, "Ask your question"))
                            }
                        }
                    } else if (message.contact() != null) {
                        val contact = message.contact()
                        val phoneNumber = contact.phoneNumber().clearPhone()

                        if (user.state == UserState.SEND_PHONE_NUMBER) {
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
                        if (text != null && text.equals("/end")) {

                        } else {
                            val session = sessionService.getOperatorSession(chatId)
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
                                botService.sendMessageToUser(session.user, savedMessage)
                            }
                        }
                    } else {
                        val sessionOpt = botService.getSession(user)
                        sessionOpt.let{session->
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
//                            session.operator?.let {
//                                botService.sendMessageToUser(session.operator!!, savedMessage)
//                            } ?: {
//                                botTools.findActiveOperator(session.user.languages[0].toString())?.let {ss->
//                                    botService.setBusy(session, ss)
//                                    botService.sendMessageToUser(session.operator!!, savedMessage)
//                                } ?: {
//                                    botService.addMessage(
//                                        session.id!!,
//                                        savedMessage,
//                                        session.user.languages[0].toString()
//                                    )
//                                }
//                            }
                            if (session.operator!=null) {
                                botService.sendMessageToUser(session.operator!!, savedMessage)
                            }else{
                                botTools.findActiveOperator(session.user.languages[0].toString())?.let {ss->
                                    botService.setBusy(session, ss)
                                    botService.sendMessageToUser(session.operator!!, savedMessage)
                                } ?: {
                                    botService.addMessage(
                                        session.id!!,
                                        savedMessage,
                                        session.user.languages[0].toString()
                                    )
                                }
                            }
                        }
                    }
                }

//                } else if (message.voice() != null) {
//                    val voice = message.voice()
//                    val fileId = voice.fileId()


                //TODO
//                    if (user.state == UserState.TALKING) {
//                        botService.sendVoiceToOperator(user, voice)
////                    }else if(user.state == UserStatus.ACTIVE){  }
//
//                } else if (message.audio() != null) {
//                    val audio = message.audio()
//                    val fileId = audio.fileId
//
//                    //TODO
////                    if (user.status == UserStatus.BUSY) {
////                        botService.sendAudioToOperator(user, audio)
////                    }
//                } else if (message.photo() != null) {
//                    val photos = message.photo()
//
//                    photos.sortByDescending { it.fileSize() }
//                    val photo = photos[photos.size / 4]
//                    val fileId = photo.fileId()
//
////                    if (user.status == UserStatus.BUSY) {
////                        if (message.caption() != null) {
////                            botService.sendPhotoWithCaptionToOperator(user, photo, message.caption())
////                        } else {
////                            botService.sendPhotoToOperator(user, photo)
////                        }
////                    }
//                } else if (message.video() != null) {
//                    val video = message.video()
//                    val fileId = video.fileId()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendVideo(chatId, video.fileId()))
//                } else if (message.animation() != null) {
//                    val animation = message.animation()
//                    val fileId = animation.fileId()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendAnimation(chatId, fileId))
//                } else if (message.sticker() != null) {
//                    val sticker = message.sticker()
//                    val fileId = sticker.fileId()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendSticker(chatId, fileId))
//                } else if (message.audio() != null) {
//                    val audio = message.audio()
//                    val fileId = audio.fileId
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendAudio(chatId, fileId))
//                } else if (message.voice() != null) {
//                    val voice = message.voice()
//                    val fileId = voice.fileId()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendVoice(chatId, fileId))
//                } else if (message.document() != null) {
//                    val document = message.document()
//                    val fileId = document.fileId()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendDocument(chatId, document.fileId()))
//                } else if (message.dice() != null) {
//                    val dice = message.dice()
//                    val emoji = dice.emoji()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendMessage(chatId, emoji))
//                } else if (message.location() != null) {
//                    val location = message.location()
//                    val lon = location.longitude()
//                    val lat = location.latitude()
//
//                    //TODO if (user is busy) send to operator
//                    bot.execute(SendLocation(chatId, lat, lon))
//                }
//            } else if (update.callbackQuery() != null) {
//                val callbackQuery = update.callbackQuery()
//                val tgUser = callbackQuery.from()
//                val chatId = tgUser.id()
//                val data = callbackQuery.data()
//
//                if (data.startsWith("setLang")) {
//                    //TODO set user's lang
//                    val lang = data.substring("setLang".length) // lang = "RU","EN","UZ"
//
//                    botService.askPhone(chatId)

            } else if (update.callbackQuery() != null) {
                val callbackQuery = update.callbackQuery()
                val tgUser = callbackQuery.from()
                val user = botService.getUser(tgUser)
                val chatId = tgUser.id()
                val data = callbackQuery.data()

                if (data.startsWith("setLang")) {
                    val lang = Language.valueOf(data.substring("setLang".length).uppercase())
                    if (!user.languages.contains(lang)) {
                        user.languages.add(lang)
                    }

                    if (user.phoneNumber.isEmpty()) {
                        botService.askPhone(user)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
