package uz.likwer.zeroonetask4supportbot.bot.backend

enum class ErrorCode(val code: Int) {
    USER_NOT_FOUND(104),
    SOMETHING_WENT_WRONG(100),
    USER_ALREADY_EXISTS(101),
    SESSION_NOT_FOUND(103),
    SESSION_ALREADY_BUSY(102),
    SESSION_CLOSED(105),
    MESSAGE_NOT_FOUND(106),
    UN_SUPPORTED_MESSAGE_TYPE(107),
    NO_SESSION_IN_QUEUE(108),
    BOT_NOT_FOUND(109)
}

enum class UserRole {
    ADMIN,
    USER,
    OPERATOR
}

enum class OperatorStatus {
    ACTIVE,
    INACTIVE,
    BUSY,
    PAUSED
}

enum class LanguageEnum {
    UZ,
    RU,
    EN
}

enum class BotMessageType {
    TEXT,
    PHOTO,
    VIDEO,
    VIDEO_NOTE,
    LOCATION,
    CONTACT,
    DICE,
    STICKER,
    AUDIO,
    ANIMATION,
    DOCUMENT,
    VOICE,
    POLL
}

enum class UserStateEnum {
    NEW_USER,
    SEND_PHONE_NUMBER,
    SEND_FULL_NAME,
    CHOOSE_LANG,
    ACTIVE_USER,
    ASK_YOUR_QUESTION,
    TALKING,
    WAITING_OPERATOR
}

enum class SessionStatusEnum {
    WAITING,
    BUSY,
    CLOSED
}

enum class BotStatusEnum {
    STOPPED,
    ACTIVE,
}