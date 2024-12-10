package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ExceptionHandler(private val errorMessageSource: ResourceBundleMessageSource) {

    @ExceptionHandler(DBusinessException::class)
    fun handleAccountException(exception: DBusinessException): ResponseEntity<BaseMessage> {
        return ResponseEntity.badRequest().body(exception.getErrorMessage(errorMessageSource))
    }
}

@RestController
@RequestMapping("api/v1/private/manage-users")
class PrivateUserController(
    private val userService: UserService,
    private val sessionService: SessionService
){

    @PutMapping("add-operator")
    fun addOperator(@RequestBody request: AddOperatorRequest)= userService.addOperator(request)

    @GetMapping("get-operators")
    fun getOperators() = userService.getAllOperators()

    @GetMapping("get-users")
    fun getUsers()= userService.getAllUsers()

    @DeleteMapping("delete-operator/{operatorId}")
    fun deleteOperator(@PathVariable operatorId: Long) = userService.deleteOperator(operatorId)

    @DeleteMapping("delete-user/{userId}")
    fun deleteUser(@PathVariable userId: Long) = userService.deleteUser(userId)

    @GetMapping("get-sessions-of-user/{userId}")
    fun getAllSessionUser(@PathVariable userId: Long, pageable: Pageable ) = sessionService.getAllSessionUser(userId,pageable)

    @GetMapping("get-sessions-of-operator/{operatorId}")
    fun getAllSessionOperator(@PathVariable operatorId: Long, pageable: Pageable ) = sessionService.getAllSessionUser(operatorId,pageable)
}