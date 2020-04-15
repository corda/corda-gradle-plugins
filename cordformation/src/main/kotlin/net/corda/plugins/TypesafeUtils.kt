package net.corda.plugins

import java.util.regex.Pattern

class TypesafeUtils {

    companion object {

        private val urlTokenPattern: Pattern = Pattern.compile("\\$\\{([^}]+)}")

        /**
         * Encodes a string that contains ${} placeholders so that can be parsed by Typesafe
         */
        fun encodeString(input: String): String {
            val matcher = urlTokenPattern.matcher(input)
            val builder = StringBuilder()
            var endOfPrevToken = 0

            while (matcher.find()) {
                val startOfCurToken = matcher.start()
                val token = matcher.group(1)
                if (startOfCurToken != 0) {
                    builder.append('\"').append(input.substring(endOfPrevToken, startOfCurToken)).append('\"')
                }
                val encodedToken = "\${$token}"
                builder.append(encodedToken)
                endOfPrevToken = startOfCurToken + encodedToken.length
            }

            if (endOfPrevToken < input.length) {
                builder.append('\"').append(input.substring(endOfPrevToken)).append('\"')
            }

            return builder.toString()
        }
    }
}