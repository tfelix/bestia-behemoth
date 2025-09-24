package net.bestia.zone.account.authentication

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import net.bestia.zone.ZoneConfig
import net.bestia.bnet.proto.EnvelopeProto
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Component
class JwtAuthenticationProcessor(
  private val zoneConfig: ZoneConfig
) : AuthenticationProcessor {

  private val key: SecretKey = Keys.hmacShaKeyFor(zoneConfig.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8))

  private data class AuthData(
    val accountId: Long,
  )

  override fun authenticate(msg: EnvelopeProto.Envelope): AuthenticationProcessor.Authentication {
    // Dummy implementation for now.
    val authRequest = msg.authentication
      ?: return AuthenticationProcessor.AuthenticationFailed

    val jwtToken = authRequest.token
    LOG.trace { "Authenticating token: $jwtToken" }
    val data = try {
      validateAndExtract(jwtToken)
    } catch (e: Exception) {
      LOG.debug(e) { "Authentication failed." }
      return AuthenticationProcessor.AuthenticationFailed
    }

    return AuthenticationProcessor.AuthenticationSuccess(accountId = data.accountId)
  }

  private fun validateAndExtract(jwtToken: String): AuthData {
    val parserBuilder = Jwts.parser()
      .verifyWith(key)

    // If configured to allow expired tokens, ignore expiration during parsing
    if (zoneConfig.allowExpiredTokens) {
      parserBuilder.clock { java.util.Date(0) }
    }

    val jws: Jws<Claims> = parserBuilder
      .build()
      .parseSignedClaims(jwtToken)

    val claims = jws.payload
    val subject = claims.subject.toLong()

    // TODO extract permissions
    return AuthData(accountId = subject)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}