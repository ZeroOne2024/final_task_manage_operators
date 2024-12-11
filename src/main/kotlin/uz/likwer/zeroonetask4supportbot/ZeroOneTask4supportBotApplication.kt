package uz.likwer.zeroonetask4supportbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import uz.likwer.zeroonetask4supportbot.backend.BaseRepositoryImpl

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
@EnableJpaRepositories(
	basePackages = ["uz.likwer.zeroonetask4supportbot.backend"],
	repositoryBaseClass = BaseRepositoryImpl::class)

class ZeroOneTask4supportBotApplication

fun main(args: Array<String>) {
	runApplication<ZeroOneTask4supportBotApplication>(*args)

	// TODO
	// Tillarga ajratib chiqish

}

