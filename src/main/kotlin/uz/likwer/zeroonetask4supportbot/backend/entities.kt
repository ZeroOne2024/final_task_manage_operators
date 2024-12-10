package uz.likwer.zeroonetask4supportbot.backend


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
    @Column(unique = true, nullable = false, length = 64) val username: String,
    @Column(nullable = false, length = 124) var fullName: String,
    @Column(nullable = false, length = 13) var phoneNumber: String,
    @ElementCollection(targetClass = Language::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_language", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    var languages: MutableList<Language> = mutableListOf(),
    @Enumerated(value = EnumType.STRING) var state: UserState = UserState.NEW_USER,
    @Enumerated(value = EnumType.STRING) var operatorStatus: OperatorStatus? = null,
    @Enumerated(value = EnumType.STRING) var role: UserRole? = UserRole.USER,
) : BaseUserEntity()


@Entity(name = "messages")
class Messages(
    @ManyToOne @JoinColumn(name = "user_id", nullable = false) val user: User,
    @ManyToOne @JoinColumn(name = "session_id", nullable = false) val session: Session,
    @Column(nullable = false) val messageId: Int,
    @Column(nullable = true) var messageBotId: Int? = null,
    @Column(nullable = true) val replyMessageId: Int? = null,
    @Enumerated(value = EnumType.STRING) val messageType: MessageType,
    @Column(nullable = true) val text: String? = null,
    @Column(nullable = true) val caption: String? = null,
    @Column(nullable = true) val fileId: String? = null,
    @OneToOne @JoinColumn(nullable = true) val location: Location? = null,
    @OneToOne @JoinColumn(nullable = true) val contact: Contact? = null,
    ) : BaseEntity()

@Entity(name = "sessions")
class Session(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Enumerated(value = EnumType.STRING)
    var status: SessionStatus=SessionStatus.WAITING,
    @ManyToOne
    @JoinColumn(name = "operator_id", nullable = true)
    var operator: User? = null,
    @Column(nullable = true)
    var rate: Short? = null
) : BaseEntity()


@Entity(name = "contacts")
class Contact(
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val phone: String,
) : BaseEntity()

@Entity(name = "location")
class Location(
    @Column(nullable = false) val latitude: Float,
    @Column(nullable = false) val longitude: Float,
) : BaseEntity()