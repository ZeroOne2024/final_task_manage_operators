package uz.likwer.zeroonetask4supportbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import uz.likwer.zeroonetask4supportbot.backend.BaseRepositoryImpl

@SpringBootApplication
@EnableJpaRepositories(
    basePackages = ["uz.likwer.zeroonetask4supportbot"],
    repositoryBaseClass = BaseRepositoryImpl::class
)
@EnableJpaAuditing
@EnableScheduling
class ZeroOneTask4supportBotApplication {

    @Bean
    fun messageSource(): ResourceBundleMessageSource {
        val messageSource = ResourceBundleMessageSource()
        messageSource.setBasenames("messages")
        messageSource.setDefaultEncoding("UTF-8")
        messageSource.setFallbackToSystemLocale(false)
        messageSource.setUseCodeAsDefaultMessage(true)
        return messageSource
    }

}

fun main(args: Array<String>) {
    runApplication<ZeroOneTask4supportBotApplication>(*args)
}
