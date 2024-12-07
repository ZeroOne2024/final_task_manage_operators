package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

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
}