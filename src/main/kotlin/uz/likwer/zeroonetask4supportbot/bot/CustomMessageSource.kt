package uz.likwer.zeroonetask4supportbot.bot

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.stereotype.Component
import java.util.*

@Component
class CustomMessageSource : ResourceBundleMessageSource() {
    fun getBundle(baseName: String, locale: Locale): ResourceBundle? {
        return super.getResourceBundle(baseName, locale)
    }
}
