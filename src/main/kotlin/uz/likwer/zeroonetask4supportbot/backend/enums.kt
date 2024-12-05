package uz.likwer.zeroonetask4supportbot.backend

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
    ACTIVE_USER,

}

enum class SessionStatus{

    WAITING,
    BUSY,
    CLOSED

}

