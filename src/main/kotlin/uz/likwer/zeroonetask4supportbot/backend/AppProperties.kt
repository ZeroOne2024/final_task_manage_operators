package uz.likwer.zeroonetask4supportbot.backend

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app")
class AppProperties {

         var username: String=""
         var fullName: String=""
         var phoneNumber: String=""


}