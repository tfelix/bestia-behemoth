package net.bestia.zoneserver.client

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.zoneserver.configuration.StaticConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.util.*

/**
 * Performs registrations of the user and setup of new accounts to the game.
 * This one gets a little tricky since registration might lead to some campaign
 * ids which then get added special items (this will most likly happen by a
 * script).
 *
 * @author Thomas Felix
 */
@Service
class RegisterService @Autowired
constructor(config: StaticConfig) {
  private val config: StaticConfig

  private class RecaptchaResponse {

    var success: Boolean = false
    var challenge_ts: String? = null
    var hostname: String? = null

    @JsonProperty("error-codes")
    var errorCodes: String? = null
  }

  init {

    this.config = Objects.requireNonNull(config)
  }

  fun isHumanUser(userResponse: String): Boolean {

    val rt = RestTemplate()

    // Create a multimap to hold the named parameters
    val parameters = LinkedMultiValueMap<String, String>()
    parameters.add("secret", config.reCaptchaSecretKey)
    parameters.add("response", userResponse)

    val response = rt.postForObject(RECAPTCHA_VERIFY_URL, parameters, RecaptchaResponse::class.java)

    return response.success
  }

  companion object {

    private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"
  }
}