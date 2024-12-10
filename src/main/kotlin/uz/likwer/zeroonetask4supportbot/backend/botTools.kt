package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList
interface BotTools{

    fun isOperator(userId: Long): Boolean

    fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?>

    fun findActiveOperator(language: String): User?

    fun getQueuedSession(operator: User): QueueResponse



}
@Service
class BotToolsImpl(
    private val userRepository: UserRepository,
) : BotTools {

    override fun isOperator(userId: Long): Boolean {
       val user = userRepository.findByIdAndDeletedFalse(userId)?: throw UserNotFoundException()
        return (user.role == UserRole.OPERATOR)
    }

    override fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?> {

            return when {
                message.text() != null ->Pair(MessageType.TEXT,null)
                message.photo() != null ->{
                    val photo = message.photo().maxByOrNull { it.fileSize() ?: 0 }
                    Pair(MessageType.PHOTO,photo?.fileId())}
                message.voice() != null -> Pair(MessageType.VOICE, message.voice().fileId())
                message.video() != null -> Pair(MessageType.VIDEO,message.video().fileId())
                message.audio() != null -> Pair(MessageType.AUDIO,message.audio().fileId)
                message.contact() != null -> Pair(MessageType.CONTACT, null)
                message.location() != null -> Pair(MessageType.LOCATION,null)
                message.sticker() != null -> Pair(MessageType.STICKER,message.sticker().fileId())
                message.animation() != null -> Pair(MessageType.ANIMATION,message.animation().fileId())
                message.document() != null -> Pair(MessageType.DOCUMENT,message.document().fileId())
                else -> throw UnSupportedMessageType()
            }
    }

    override fun findActiveOperator(language: String): User? {
        return userRepository.findFirstByRoleAndOperatorStatusAndDeletedFalseOrderByModifiedDateAsc(UserRole.OPERATOR,OperatorStatus.ACTIVE)
    }

    @Synchronized
    override fun getQueuedSession(operator: User): QueueResponse {
        val languages = operator.languages
        val languageToQueueMap = mapOf(
            Language.UZ to DataLoader.queueUz,
            Language.RU to DataLoader.queueRu,
            Language.EN to DataLoader.queueEn
        )

        var smallestSession: Long? = null
        var smallestQueue: Map<Long, CopyOnWriteArrayList<Messages>>? = null

        for (language in languages) {
            val queue = languageToQueueMap[language]
            val currentSession = queue?.keys?.minOrNull()
            if (currentSession != null && (smallestSession == null || currentSession < smallestSession)) {
                smallestSession = currentSession
                smallestQueue = queue
            }
        }

        return smallestSession?.let {
            QueueResponse(it, smallestQueue!![it]!!)
        } ?: throw NoSessionInQueue()
    }



}
