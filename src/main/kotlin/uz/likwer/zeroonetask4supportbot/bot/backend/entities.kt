package uz.likwer.zeroonetask4supportbot.bot.backend

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.util.*

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false
)

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseUserEntity(
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false
)

@Entity(name = "users")
class User(
    @Id @Column(nullable = false) var id: Long,
    @Column(nullable = false, length = 64) val username: String,
    @Column(nullable = false, length = 124) var fullName: String,
    @Column(nullable = false, length = 13) var phoneNumber: String,
    @Column(nullable = false) var botId: Long,
    @ElementCollection(targetClass = LanguageEnum::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_language", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    var languages: MutableSet<LanguageEnum> = mutableSetOf(),
    @Enumerated(value = EnumType.STRING) var state: UserStateEnum = UserStateEnum.NEW_USER,
    @Enumerated(value = EnumType.STRING) var operatorStatus: OperatorStatus? = null,
    @Enumerated(value = EnumType.STRING) var role: UserRole? = UserRole.USER,

    var msgIdChooseLanguage: Int? = null,
) : BaseUserEntity() {
    fun isOperator(): Boolean {
        return role == UserRole.OPERATOR
    }

    fun isUser(): Boolean {
        return role == UserRole.USER
    }

    fun isTalking(): Boolean {
        return state == UserStateEnum.TALKING || operatorStatus == OperatorStatus.BUSY
    }
    fun isActiveOperator(): Boolean {
        return operatorStatus == OperatorStatus.ACTIVE
    }
}

@Entity
class Session(
    @ManyToOne val user: User,
    val botId: Long,
    @Enumerated(EnumType.STRING) var status: SessionStatusEnum? = SessionStatusEnum.WAITING,
    @ManyToOne var operator: User? = null,
    var rate: Short? = null,
) : BaseEntity() {
    fun hasOperator(): Boolean {
        return operator != null
    }
    fun isClosed():Boolean{
        return status == SessionStatusEnum.CLOSED
    }
}


@Entity(name = "double_operator")
class DoubleOperator(
    @ManyToOne val operator: User,
    @ManyToOne val session: Session
) : BaseEntity()

@Entity(name = "location")
class Location(
    @Column(nullable = false) val latitude: Float,
    @Column(nullable = false) val longitude: Float,
) : BaseEntity()

@Entity(name = "dice")
class Dice(
    @Column(nullable = false) val value: Int,
    @Column(nullable = false) val emoji: String,
) : BaseEntity()

@Entity
class Bot(
    @Column(nullable = false) val token: String,
    @Column(nullable = false) val username: String,
    val name: String,
    @Enumerated(value = EnumType.STRING) var status: BotStatusEnum = BotStatusEnum.ACTIVE,
) : BaseEntity()

@Entity(name = "bot_message")
class BotMessage(
    @ManyToOne val user: User,
    @ManyToOne val session: Session,
    @Column(nullable = false) val messageId: Int,
    @Column(nullable = true) var botMessageId: Int?=null,
    @Column(nullable = true) val replyMessageId: Int? = null,
    @Column(nullable = true) var text: String? = null,
    @Column(nullable = true) var caption: String? = null,
    @Enumerated(value = EnumType.STRING) val botMessageType: BotMessageType,
    @Column(nullable = true) val fileId: String? = null,
    @OneToOne @JoinColumn(nullable = true) val location: Location? = null,
    @OneToOne @JoinColumn(nullable = true) val contact: Contact? = null,
    @OneToOne @JoinColumn(nullable = true) val dice: Dice? = null
) : BaseEntity()


@Entity
class Contact(
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val phoneNumber: String,
) : BaseEntity()