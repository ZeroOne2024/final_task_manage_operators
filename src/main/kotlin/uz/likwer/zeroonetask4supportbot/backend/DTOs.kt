package uz.likwer.zeroonetask4supportbot.backend

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

data class BaseMessage(val code: Int, val message: String?)

data class AddOperatorRequest(
    val userId: Long,
    val userRole: UserRole,
    val languages: MutableList<Language>
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
                return UserResponse(id!!,username,fullName,phoneNumber,languages,role)
            }
        }
    }
}


data class SessionInfo(
    val user: UserResponse,
    val status: SessionStatus,
    val operator: UserResponse?,
    val rate: Short?
)

data class RateInfo(
    val rate: Short,
    val operator: UserResponse,
)

data class DateRangeDTO(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    val fromDate: LocalDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    val toDate: LocalDateTime
)

data class QueueResponse(
    val sessionId: Long,
    val messages: CopyOnWriteArrayList<Messages>
)