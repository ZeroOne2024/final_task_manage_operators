package uz.likwer.zeroonetask4supportbot.backend

import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service


interface UserService {

    fun addOperator(request: AddOperatorRequest ) : UserResponse

    fun getAllOperators() : List<UserResponse>

    fun getAllUsers() : List<UserResponse>

    fun deleteOperator(operatorId: Long) : UserResponse

    fun deleteUser(userId: Long)
}

interface SessionService {
    fun getSession(userId: Long): Session
    fun setBusy(sessionId:Long,operatorId: Long): Session
    fun getAllSession(pageable: Pageable): Page<SessionInfo>
    fun getOne(id:Long): SessionInfo
    fun terminateSession(operatorId: Long):Session
    fun setRate(userId: Long, rate:Short):Session
    fun getAllSessionUser(userId: Long,pageable: Pageable) : Page<SessionInfo>
    fun getAllSessionOperator(operatorId: Long, pageable: Pageable) : Page<SessionInfo>
    fun getAllSessionUserDateRange(userId: Long,dto: DateRangeDTO, pageable: Pageable) : Page<SessionInfo>
    fun getAllSessionOperatorDateRange(operatorId: Long,dto: DateRangeDTO, pageable: Pageable) : Page<SessionInfo>
    fun getSessionsByStatus(status: SessionStatus, pageable: Pageable) : Page<SessionInfo>
    fun getHighRateOperator(pageable: Pageable):Page<RateInfo>
    fun getLowRateOperator(pageable: Pageable):Page<RateInfo>
    fun getHighRateOperatorDateRange(dto: DateRangeDTO, pageable: Pageable): Page<RateInfo>
    fun getLowRateOperatorDateRange(dto: DateRangeDTO, pageable: Pageable): Page<RateInfo>
    fun getOperatorRate(operatorId: Long,pageable: Pageable): Page<RateInfo>
}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {
    override fun addOperator(request: AddOperatorRequest): UserResponse {

        request.run {
            var user : User
            val optional = userRepository.findById(userId)
            if(optional.isEmpty) throw UserNotFoundException()
            user = optional.get()
            userRole.let { user.role = it }
            languages.let { user.languages = it }
            user.operatorStatus = OperatorStatus.INACTIVE
            return UserResponse.toResponse(userRepository.save(user))
        }

    }

    override fun getAllOperators(): List<UserResponse> {
        return  userRepository.findAllByRole(UserRole.OPERATOR).map {
             UserResponse.toResponse(it)
        }
    }

    override fun getAllUsers(): List<UserResponse> {
        return userRepository.findAll().map {
            UserResponse.toResponse(it)
        }
    }

    override fun deleteOperator(operatorId: Long): UserResponse {

        val optional = userRepository.findById(operatorId)
        if(optional.isEmpty) throw UserNotFoundException()
        val user = optional.get()
        user.role = UserRole.USER
        return UserResponse.toResponse(userRepository.save(user))
    }
}

@Service
class SessionServiceImpl(private val sessionRepository:SessionRepository,
    private val userRepository: UserRepository):SessionService {

    override fun getAllSession(pageable: Pageable): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.findAll(pageable))
    }

    override fun getOne(id: Long): SessionInfo {
        val session = sessionRepository.findById(id)
            .orElseThrow { SessionNotFoundExistException() }
        return toSessionInfo(session)
    }
    @Transactional
    override fun getSession(userId: Long): Session {
        return sessionRepository.findLastSessionByUserId(userId)?.run {
            if (status == SessionStatus.CLOSED) {
                userRepository.findByIdAndDeletedFalse(userId)
                    ?.let { sessionRepository.save(Session(it)) }
                    ?: throw UserNotFoundException()
            } else {
                this
            }
        } ?: userRepository.findByIdAndDeletedFalse(userId)
            ?.let { sessionRepository.save(Session(it)) }
        ?: throw UserNotFoundException()
    }


    @Transactional
    override fun terminateSession(operatorId: Long): Session {
        return sessionRepository.findLastSessionByOperatorId(operatorId)?.run {
            status=SessionStatus.CLOSED
            sessionRepository.save(this)
        }?:throw SessionNotFoundExistException()
    }

    @Transactional
    override fun setBusy(sessionId: Long, operatorId: Long): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { SessionNotFoundExistException() }

        val setOperator = userRepository.findByIdAndDeletedFalse(operatorId)?:throw UserNotFoundException()

        return session.run {
            if (status != SessionStatus.WAITING) throw SessionAlreadyBusyException()

            status = SessionStatus.BUSY
            operator = setOperator
            sessionRepository.save(this)
        }
    }

    @Transactional
    override fun setRate(userId: Long, rate: Short): Session {
        return sessionRepository.findLastSessionByUserId(userId)?.run {
            if (status != SessionStatus.BUSY) throw SessionClosedException()
            this.rate=rate
            sessionRepository.save(this)
        }?: throw SessionNotFoundExistException()
    }

    override fun getAllSessionUser(userId: Long, pageable: Pageable): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.getSessionByUserId(userId, pageable))
    }


    override fun getAllSessionOperator(operatorId: Long, pageable: Pageable): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.getSessionByOperatorId(operatorId, pageable))
    }

    override fun getAllSessionUserDateRange(
        userId: Long,
        dto: DateRangeDTO,
        pageable: Pageable
    ): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.findAllSessionsByUserAndDateRange(userId,dto.fromDate,dto.toDate,pageable))
    }

    override fun getAllSessionOperatorDateRange(
        operatorId: Long,
        dto: DateRangeDTO,
        pageable: Pageable
    ): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.findAllSessionsByOperatorAndDateRange(operatorId,dto.fromDate, dto.toDate,pageable))
    }

    override fun getSessionsByStatus(status: SessionStatus, pageable: Pageable): Page<SessionInfo> {
        return toSessionInfo(sessionRepository.getSessionByStatus(status, pageable))
    }

    override fun getHighRateOperator(pageable: Pageable): Page<RateInfo> {
        return toRateInfo(sessionRepository.findHighestRatedOperators(pageable))
    }

    override fun getLowRateOperator(pageable: Pageable): Page<RateInfo> {
        return toRateInfo(sessionRepository.findLowestRatedOperators(pageable))
    }

    override fun getHighRateOperatorDateRange(dto: DateRangeDTO, pageable: Pageable): Page<RateInfo> {
        return toRateInfo(sessionRepository.findHighestRatedOperatorsByDateRange(dto.fromDate,dto.toDate, pageable))
    }

    override fun getLowRateOperatorDateRange(dto: DateRangeDTO, pageable: Pageable): Page<RateInfo> {
        return toRateInfo(sessionRepository.findLowestRatedOperatorsByDateRange(dto.fromDate,dto.toDate, pageable))

    }

    override fun getOperatorRate(operatorId: Long, pageable: Pageable): Page<RateInfo> {
        return toRateInfo(sessionRepository.findOperatorRates(operatorId, pageable))
    }
    private fun toSessionInfo(sessions: Page<Session>): Page<SessionInfo> {
        return sessions.map { session ->
            SessionInfo(
                user = UserResponse.toResponse(session.user),
                status = session.status,
                operator = session.operator?.let { UserResponse.toResponse(it) },
                rate = session.rate
            )
        }
    }
    private fun toSessionInfo(session: Session): SessionInfo {
        return SessionInfo(
            user = UserResponse.toResponse(session.user),
            status = session.status,
            operator = session.operator?.let { UserResponse.toResponse(it) },
            rate = session.rate
        )
    }

    private fun toRateInfo(results: Page<Array<Any>>): Page<RateInfo> {
        return results.map { result ->
            val operator = result[0] as User
            val totalRate = result[1] as Number
            RateInfo(rate = totalRate.toShort(), operator = UserResponse.toResponse(operator))
        }
    }

    override fun deleteUser(userId: Long) {

        val optional = userRepository.findById(userId)
        if(optional.isEmpty) throw UserNotFoundException()
        val user = optional.get()
        user.deleted = true
        userRepository.save(user)
    }
}