package uz.likwer.zeroonetask4supportbot.backend

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.*

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): List<T>
    fun findAllNotDeletedForPageable(pageable: Pageable): Page<T>
    fun saveAndRefresh(t: T): T
}

@EnableJpaRepositories(repositoryBaseClass = BaseRepositoryImpl::class)
class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): List<T> = findAll(isNotDeletedSpecification, pageable).content
    override fun findAllNotDeletedForPageable(pageable: Pageable): Page<T> =
        findAll(isNotDeletedSpecification, pageable)

    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

    @Transactional
    override fun saveAndRefresh(t: T): T {
        return save(t).apply { entityManager.refresh(this) }
    }
}

interface UserRepository : JpaRepository<User, Long> {
    fun existsByRole(role: UserRole): Boolean
    fun findAllByRoleAndDeletedFalse(userRole: UserRole): List<User>
    fun findFirstByRoleAndOperatorStatusAndDeletedFalseOrderByModifiedDateAsc(
        role: UserRole,
        operatorStatus: OperatorStatus
    ): User?

    @Query(
        """
        SELECT u
        FROM users u
        WHERE u.role = :role
          AND u.operatorStatus = :status
        ORDER BY u.modifiedDate ASC
    """
    )
    fun findFirstActiveOperator(
        @Param("role") role: UserRole,
        @Param("status") status: OperatorStatus
    ): Optional<User>


}

interface MessageRepository : BaseRepository<Messages> {
    fun findByUserIdAndMessageBotId(userId: Long, messageBotId: Int): Messages?
    fun findAllBySessionId(sessionId: Long): List<Messages>
    fun findAllByUserId(userId: Long): List<Messages>
}

interface SessionRepository : BaseRepository<Session> {
    @Query(
        """
    SELECT s.operator, SUM(s.rate)
    FROM sessions s 
    WHERE s.rate IS NOT NULL 
      AND s.createdDate BETWEEN :fromDate AND :toDate
    GROUP BY s.operator 
    ORDER BY SUM(s.rate) DESC
    """
    )
    fun findHighestRatedOperatorsByDateRange(
        @Param("fromDate") fromDate: Date,
        @Param("toDate") toDate: Date,
        pageable: Pageable
    ): Page<Array<Any>>

    @Query(
        """
    SELECT s.operator, SUM(s.rate)
    FROM sessions s 
    WHERE s.rate IS NOT NULL 
      AND s.createdDate BETWEEN :fromDate AND :toDate
    GROUP BY s.operator 
    ORDER BY SUM(s.rate) ASC
    """
    )
    fun findLowestRatedOperatorsByDateRange(
        @Param("fromDate") fromDate: Date,
        @Param("toDate") toDate: Date,
        pageable: Pageable
    ): Page<Array<Any>>

    @Query(
        """
    SELECT s.operator, s.rate
    FROM sessions s
    WHERE s.operator.id = :operatorId
      AND s.rate IS NOT NULL
    """
    )
    fun findOperatorRates(
        @Param("operatorId") operatorId: Long,
        pageable: Pageable
    ): Page<Array<Any>>

    @Query(
        """
    SELECT s
    FROM sessions s
    WHERE s.operator.id = :operatorId
      AND s.createdDate BETWEEN :fromDate AND :toDate
    """
    )
    fun findAllSessionsByOperatorAndDateRange(
        @Param("operatorId") operatorId: Long,
        @Param("fromDate") fromDate: Date,
        @Param("toDate") toDate: Date,
        pageable: Pageable
    ): Page<Session>

    @Query(
        """
    SELECT s
    FROM sessions s
    WHERE s.user.id = :userId
      AND s.createdDate BETWEEN :fromDate AND :toDate
    """
    )
    fun findAllSessionsByUserAndDateRange(
        @Param("userId") userId: Long,
        @Param("fromDate") fromDate: Date,
        @Param("toDate") toDate: Date,
        pageable: Pageable
    ): Page<Session>


    @Query(
        """
    SELECT s.operator,sum(s.rate)
    FROM sessions s 
    WHERE s.rate IS NOT NULL 
    GROUP BY s.operator 
    ORDER BY sum(s.rate) DESC
    """
    )
    fun findHighestRatedOperators(pageable: Pageable): Page<Array<Any>>

    @Query(
        """
    SELECT s.operator,sum(s.rate)
    FROM sessions s 
    WHERE s.rate IS NOT NULL 
    GROUP BY s.operator 
    ORDER BY sum(s.rate) ASC
    """
    )
    fun findLowestRatedOperators(pageable: Pageable): Page<Array<Any>>

    @Query(
        "SELECT s FROM sessions s " +
                "WHERE s.user.id = :userId " +
                "ORDER BY s.createdDate DESC"
    )
    fun findLastSessionByUserId(@Param("userId") userId: Long): Session?

    @Query(
        "SELECT s FROM sessions s " +
                "WHERE s.operator.id = :operatorId " +
                "ORDER BY s.createdDate DESC"
    )
    fun findLastSessionByOperatorId(@Param("operatorId") operatorId: Long): Session?
    fun getSessionByUserId(userId: Long, pageable: Pageable): Page<Session>
    fun getSessionByOperatorId(operatorId: Long, pageable: Pageable): Page<Session>
    fun getSessionByStatus(status: SessionStatus, pageable: Pageable): Page<Session>

}

interface LocationRepository : BaseRepository<Location>
interface ContactRepository : BaseRepository<Contact>
