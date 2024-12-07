package uz.likwer.zeroonetask4supportbot.backend

enum class ErrorCode(val code: Int) {

    USER_NOT_FOUND(104),
    SOMETHING_WENT_WRONG(100),
    USER_ALREADY_EXISTS(101),
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
    CONTACT,
    VIDEO_NOTE,
    STICKER,
    AUDIO,
    ANIMATION,
    DOCUMENT,
    GAME,
    INVOICE,
    LOCATION,
    POLL,
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

