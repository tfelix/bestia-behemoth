package net.bestia.zoneserver.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Holds configuration variables for the server. These config variables are
 * either obtained by application.yml or via commandline attributes. They
 * are not meant to be changed during runtime.
 *
 * @author Thomas Felix
 */
@Component
data class StaticConfig(
        /**
         * Returns the name of this server. By default this is an auto generated
         * value.
         *
         * @return The server name.
         */
        @Value("\${server.name}")
        val serverName: String? = null,

        /**
         * Returns the size of the entity buffer inside the recycler.
         *
         * @return Size of the entity buffer.
         */
        @Value("\${server.entityBuffer:10}")
        val entityBufferSize: Int = 10,

        /**
         * Directory of the script files.
         */
        /**
         * Returns the script directories for the custom item, mob, entity scripts
         * etc.
         *
         * @return The path to the scripts.
         */
        @Value("\${script.path}")
        val scriptDir: String? = null,

        /**
         * Returns the server string of its version.
         *
         * @return The server version.
         */
        @Value("\${server.version}")
        val serverVersion: String? = null,

        /**
         * Returns the secret key for re-captcha authentication.
         *
         * @return The server secret key.
         */
        @Value("\${recaptcha.secretKey}")
        val reCaptchaSecretKey: String? = null
)

