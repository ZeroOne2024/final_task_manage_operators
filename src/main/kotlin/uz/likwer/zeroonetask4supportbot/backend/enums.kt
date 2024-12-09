package uz.likwer.zeroonetask4supportbot.backend

enum class ErrorCode(val code: Int) {

    USER_NOT_FOUND(104),
    SOMETHING_WENT_WRONG(100),
    USER_ALREADY_EXISTS(101),
    SESSION_NOT_FOUND(105),
    SESSION_ALREADY_BUSY(106),
    SESSION_CLOSED(107),
    UN_SUPPORTED_MESSAGE_TYPE(108)
}

enum class UserRole{

    ADMIN,
    USER,
    OPERATOR

}

enum class OperatorStatus{

    ACTIVE,
    INACTIVE,
    BUSY,
    PAUSED

}

enum class Language{

    UZ,
    RU,
    EN

}

enum class MessageType{
    TEXT,
    PHOTO,
    VIDEO,
    VIDEO_NOTE,
    CONTACT,
    STICKER,
    AUDIO,
    ANIMATION,
    DOCUMENT,
    GAME,
    INVOICE,
    LOCATION,
    VOICE
}

enum class UserState{

    NEW_USER,
    SEND_PHONE_NUMBER,
    SEND_FULL_NAME,
    SEND_CHOOSE_LANG,
    ACTIVE_USER,
    TALKING
}

enum class SessionStatus{

    WAITING,
    BUSY,
    CLOSED

}

