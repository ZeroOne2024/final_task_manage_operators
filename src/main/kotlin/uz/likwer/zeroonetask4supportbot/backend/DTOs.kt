package uz.likwer.zeroonetask4supportbot.backend

data class BaseMessage(val code: Int, val message: String?)

data class AddOperatorRequest(
    val userId: Long,
    val userRole: UserRole,
    val languages: List<Language>
)

data class UserResponse(
    val id: Long,
    val username: String,
    val fullName: String,
    val phoneNumber: String,
    val language: List<Language>,
    val role: UserRole?
){
    companion object{
        fun toResponse(user: User): UserResponse{
            user.run {
                return UserResponse(id,username,fullName,phoneNumber,languages,role)
            }
        }
    }
}