package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource

sealed class DBusinessException : RuntimeException() {

    abstract fun errorCode(): ErrorCode

    open fun getErrorMessageArguments(): Array<Any?>? = null

    fun getErrorMessage(errorMessageSource: ResourceBundleMessageSource): BaseMessage {
        val errorMessage = try {
            errorMessageSource.getMessage(errorCode().name, getErrorMessageArguments(), LocaleContextHolder.getLocale())
        } catch (e: Exception) {
            e.message
        }
        return BaseMessage(errorCode().code, errorMessage)
    }
}


class UserNotFoundException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.USER_NOT_FOUND
}

class UserAlreadyExistException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.USER_ALREADY_EXISTS
}

class SomethingWentWrongException : DBusinessException(){
    override fun errorCode(): ErrorCode = ErrorCode.SOMETHING_WENT_WRONG
}
class SessionNotFoundExistException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.SESSION_NOT_FOUND
}
class SessionAlreadyBusyException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.SESSION_ALREADY_BUSY
}
class SessionClosedException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.SESSION_CLOSED
}
class MessageNotFoundException : DBusinessException() {
    override fun errorCode(): ErrorCode = ErrorCode.MESSAGE_NOT_FOUND
}
