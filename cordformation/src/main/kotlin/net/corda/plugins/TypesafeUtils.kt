package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
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
                builder.append("\${").append(token).append('}')
                endOfPrevToken = startOfCurToken + token.length + "\${}".length
            }

            if (endOfPrevToken < input.length) {
                builder.append('\"').append(input.substring(endOfPrevToken)).append('\"')
            }

            return builder.toString()
        }

        /**
         * Resolves the placeholders parameters in an input string
         */
        fun resolveString(input: String, args: Config): String {
            return ConfigFactory.parseString("INPUT=${encodeString(input)}")
                    .resolveWith(args)
                    .getString("INPUT")
        }
    }
}