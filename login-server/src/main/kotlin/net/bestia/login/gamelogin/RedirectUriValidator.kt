package net.bestia.login.gamelogin

import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException

/**
 * Accepts only literal loopback redirect targets, per RFC 8252 section 7.3.
 *
 * This is a hard security boundary, not a sanity check. The redirect target is chosen by the game
 * client, which is untrusted; without this the login server would happily mail an authorization
 * code to any host an attacker named, which is the classic open-redirector token leak.
 */
@Component
class RedirectUriValidator {

  fun validate(raw: String): URI {
    if (raw.length > MAX_LENGTH) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri exceeds $MAX_LENGTH characters")
    }

    val uri = try {
      URI(raw)
    } catch (e: URISyntaxException) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri is not a URI", e)
    }

    // Loopback redirects are the one place RFC 8252 permits plain http, because the response never
    // leaves the machine.
    if (uri.scheme != "http") {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri must use http, was ${uri.scheme}")
    }

    // "localhost" is rejected deliberately: it is a name, and a hosts file or a DNS search domain
    // can point it somewhere off-box. The literals cannot be redirected.
    if (uri.host !in LOOPBACK_HOSTS) {
      throw GameLoginException(
        GameLoginError.INVALID_REQUEST,
        "redirect_uri host must be a loopback literal, was ${uri.host}"
      )
    }

    if (uri.port !in MIN_PORT..MAX_PORT) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri port ${uri.port} out of range")
    }

    if (uri.userInfo != null) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri must not carry user info")
    }

    // The code and state are appended by us. Anything already there would either be dropped or
    // silently merged, and neither is worth the ambiguity.
    if (uri.rawQuery != null || uri.rawFragment != null) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "redirect_uri must not carry a query or fragment")
    }

    return uri
  }

  companion object {
    // URI parses an IPv6 literal host including its brackets.
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "[::1]")
    private const val MIN_PORT = 1024
    private const val MAX_PORT = 65535
    private const val MAX_LENGTH = 255
  }
}
