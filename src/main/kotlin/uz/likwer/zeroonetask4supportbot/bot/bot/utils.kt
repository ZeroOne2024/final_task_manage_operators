package uz.likwer.zeroonetask4supportbot.bot.bot

class Utils {
    companion object {
        fun String.prettyPhoneNumber(): String {
            return try {
                var phone = this.trim()
                if (phone.startsWith("+"))
                    phone = phone.substring(1)
                if (phone.startsWith("998")) {
                    val regex = "(\\d{3})(\\d{2})(\\d{3})(\\d{2})(\\d{2})".toRegex()
                    phone.replace(regex, "+$1 $2 $3 $4 $5")
                } else
                    "+$phone"
            } catch (e: Exception) {
                this
            }
        }

        fun String.clearPhone(): String {
            return try {
                this.replace(Regex("[^0-9]"), "")
            } catch (e: Exception) {
                this
            }
        }

        fun String.htmlBold(): String {
            return "<b>$this</b>"
        }

        fun String.htmlItalic(): String {
            return "<i>$this</i>"
        }

        fun String.htmlA(href: String): String {
            return "<a href=\"$href\">$this</a>"
        }

        fun String.htmlUnderline(): String {
            return "<u>$this</u>"
        }

        fun String.htmlStrikeThrough(): String {
            return "<s>$this</s>"
        }
    }
}