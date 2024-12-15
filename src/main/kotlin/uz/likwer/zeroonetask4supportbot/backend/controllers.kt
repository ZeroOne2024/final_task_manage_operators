package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.Page
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
@RequestMapping("/api/sessions")
class SessionController(private val sessionService: SessionService) {

    @GetMapping
    fun getAllSessions(pageable: Pageable): Page<SessionInfo> {
        return sessionService.getAllSession(pageable)
    }

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: Long): SessionInfo {
        return sessionService.getOne(id)
    }

    @GetMapping("/user/{userId}")
    fun getAllSessionUser(@PathVariable userId: Long, pageable: Pageable): Page<SessionInfo> {
        return sessionService.getAllSessionUser(userId, pageable)
    }

    @GetMapping("/operator/{operatorId}")
    fun getAllSessionOperator(@PathVariable operatorId: Long, pageable: Pageable): Page<SessionInfo> {
        return sessionService.getAllSessionOperator(operatorId, pageable)
    }

    @GetMapping("/operator/average")
    fun getAllSessionOperator(pageable: Pageable): Page<RateInfo> {
        return sessionService.getOperatorsAverageRates(pageable)
    }

    @PostMapping("/user/{userId}")
    fun getAllSessionUserDateRange(
        @PathVariable userId: Long,
        @RequestBody dto: DateRangeDTO,
        pageable: Pageable
    ): Page<SessionInfo> {
        return sessionService.getAllSessionUserDateRange(userId, dto, pageable)
    }

    @PostMapping("/operator/{operatorId}")
    fun getAllSessionOperatorDateRange(
        @PathVariable operatorId: Long,
        @RequestBody  dto: DateRangeDTO,
        pageable: Pageable
    ): Page<SessionInfo> {
        return sessionService.getAllSessionOperatorDateRange(operatorId, dto, pageable)
    }

    @GetMapping("/status")
    fun getSessionsByStatus(@RequestParam status: SessionStatus, pageable: Pageable): Page<SessionInfo> {
        return sessionService.getSessionsByStatus(status, pageable)
    }

    @GetMapping("/operators/high-rate")
    fun getHighRateOperator(pageable: Pageable): Page<RateInfo> {
        return sessionService.getHighRateOperator(pageable)
    }

    @GetMapping("/operators/low-rate")
    fun getLowRateOperator(pageable: Pageable): Page<RateInfo> {
        return sessionService.getLowRateOperator(pageable)
    }

    @PostMapping("/operators/high-rate")
    fun getHighRateOperatorDateRange(
        @RequestBody dto: DateRangeDTO,
        pageable: Pageable
    ): Page<RateInfo> {
        return sessionService.getHighRateOperatorDateRange(dto, pageable)
    }

    @PostMapping("/operators/low-rate")
    fun getLowRateOperatorDateRange(
        @RequestBody dto: DateRangeDTO,
        pageable: Pageable
    ): Page<RateInfo> {
        return sessionService.getLowRateOperatorDateRange(dto, pageable)
    }

    @GetMapping("/operators/rate/{operatorId}")
    fun getOperatorRate(@PathVariable operatorId: Long, pageable: Pageable): Page<RateInfo> {
        return sessionService.getOperatorRate(operatorId, pageable)
    }
}

@RestController
@RequestMapping("/api/messages")
class MessageController(private val messageService: MessageService) {

    @GetMapping("/most-send-operators")
    fun getMostSendMessageOperators(pageable: Pageable): Page<MessageInfo> {
        return messageService.getMostSendMessageOperators(pageable)
    }
    @GetMapping("/most-send-users")
    fun getMostSendMessageUsers(pageable: Pageable): Page<MessageInfo> {
        return messageService.getMostSendMessageUsers(pageable)
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

    @GetMapping("get-operator/{id}")
    fun getOperatorById(@PathVariable id: Long) = userService.getOperatorById(id)

    @GetMapping("get-user/{id}")
    fun getUserById(@PathVariable id: Long) = userService.getUserById(id)

//    @GetMapping("get-sessions-of-operator/{operatorId}")
//    fun getAllSessionOperator(@PathVariable operatorId: Long, pageable: Pageable ) = sessionService.getAllSessionUser(operatorId,pageable)


}