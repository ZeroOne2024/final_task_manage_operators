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
    var userId: Int,
    @Column(unique = true,nullable = false,length = 64)
    var username: String,
    @Column(nullable = false,length = 124)
    var fullName: String,
    @Column(unique=true,nullable = false, length = 13)
    var phoneNumber: String,
    @Enumerated(value = EnumType.STRING)
    var role: UserRole,
    @ElementCollection(targetClass = Language::class)
    @CollectionTable(
        name = "user_language",
        joinColumns = [JoinColumn(name = "user_id")]
    )
    @Enumerated(EnumType.STRING)
    var languages: List<Language> = mutableListOf(),
    @Enumerated(value = EnumType.STRING)
    var state: UserState
) : BaseEntity()

@Entity(name = "messages")
class Messages(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    var session: Session,
    var text: String,
    @Enumerated(value = EnumType.STRING)
    var messageType: MessageType,
    var messageDate: Date=Date()
):BaseEntity()

@Entity(name = "sessions")
class Session(
    @ManyToOne
    @JoinColumn(name = "operator_id", nullable = false)
    var operator: User,
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Enumerated(value = EnumType.STRING)
    var status: SessionStatus,
    @Column(nullable = true)
    var rate: Short? = null,
    var sessionDate: Date=Date()
):BaseEntity()

@Entity(name = "files")
class File(
    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    var messages: Messages,
    @Column(nullable = false)
    var fileId: Long
):BaseEntity()