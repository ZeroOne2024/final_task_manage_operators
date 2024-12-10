package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class DataLoader(
    private val userRepository: UserRepository,
    private val appProperties: AppProperties
) : CommandLineRunner {
    override fun run(vararg args: String?) {

        if (!userRepository.existsByRole(UserRole.ADMIN)) {

            val admin = User(
                1,
                appProperties.username,
                appProperties.fullName,
                appProperties.phoneNumber,
                mutableListOf(Language.EN, Language.UZ, Language.RU),
                UserState.NEW_USER,
                null,
                UserRole.ADMIN
            )

            userRepository.save(admin)
        }
    }


    companion object {
        val queueEn = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>()
        val queueUz = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>()
        val queueRu = ConcurrentHashMap<Long, CopyOnWriteArrayList<Messages>>()
    }


}