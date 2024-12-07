package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.stereotype.Service

interface UserService {

    fun addOperator(request: AddOperatorRequest ) : UserResponse

    fun getAllOperators() : List<UserResponse>

    fun getAllUsers() : List<UserResponse>

    fun deleteOperator(operatorId: Long) : UserResponse

    fun deleteUser(userId: Long)
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

    override fun deleteUser(userId: Long) {

        val optional = userRepository.findById(userId)
        if(optional.isEmpty) throw UserNotFoundException()
        val user = optional.get()
        user.deleted = true
        userRepository.save(user)
    }
}