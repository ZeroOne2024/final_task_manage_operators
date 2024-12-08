package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class DataLoader(
    private val userRepository: UserRepository,
    private val appProperties: AppProperties
): CommandLineRunner {
    override fun run(vararg args: String?) {

        if(!userRepository.existsByRole(UserRole.ADMIN)){

            val admin =User(1,appProperties.username,appProperties.fullName,appProperties.phoneNumber, mutableListOf(Language.EN,Language.UZ,Language.RU),
                UserState.NEW_USER,null,null,UserRole.ADMIN)

            userRepository.save(admin)
        }
    }

    // Userlarni navbatini manage qilish uchun

    var queueMapUz: MutableMap<Long, List<Messages>> = ConcurrentHashMap()
    var queueMapRu: MutableMap<Long, List<Messages>> = ConcurrentHashMap()
    var queueMapEn: MutableMap<Long, List<Messages>> = ConcurrentHashMap()

}