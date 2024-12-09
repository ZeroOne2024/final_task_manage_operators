package uz.likwer.zeroonetask4supportbot.backend

interface BotTools{

    fun isOperator(userId: Long): Boolean

    fun determineMessageType(message: com.pengrad.telegrambot.model.Message): Pair<MessageType, String?>

    fun findActiveOperator(languageCode: String): User?

}

class BotToolsImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository
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

    override fun findActiveOperator(languageCode: String): User? {
        return userRepository.findFirstByRoleAndOperatorStatusAndDeletedFalseOrderByModifiedDateAsc(UserRole.OPERATOR,OperatorStatus.ACTIVE)
    }
}
