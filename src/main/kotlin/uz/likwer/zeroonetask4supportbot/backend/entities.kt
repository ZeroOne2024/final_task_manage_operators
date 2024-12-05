package uz.likwer.zeroonetask4supportbot.backend


import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.util.*

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @CreatedBy var createdBy: Long? = null,
    @LastModifiedBy var lastModifiedBy: Long? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false
)

@Entity(name = "users")
class User(
    @Column(unique = true,nullable = false)
    val clientId: Int,
    @Column(unique = true,nullable = false,length = 64)
    val username: String,
    @Column(nullable = false,length = 124)
    val fullName: String,
    @Column(unique=true,nullable = false, length = 13)
    val phoneNumber: String,
    @Enumerated(value = EnumType.STRING)
    val role: UserRole,
    @Enumerated(value = EnumType.STRING)
    val language: Language,
    @Enumerated(value = EnumType.STRING)
    val state: UserState
) : BaseEntity()

@Entity(name = "messages")
class Messages(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    val session: Session,
    val text: String,
    @Enumerated(value = EnumType.STRING)
    val messageType: MessageType
):BaseEntity()

@Entity(name = "sessions")
class Session(
    @Column(nullable = true)
    val rate: Short,
    @ManyToOne
    @JoinColumn(name = "operator_id", nullable = false)
    val operator: User,
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Enumerated(value = EnumType.STRING)
    val status: SessionStatus
):BaseEntity()

@Entity(name = "files")
class File(
    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    val messages: Messages,
    @Column(nullable = false)
    val fileId: Long
):BaseEntity()