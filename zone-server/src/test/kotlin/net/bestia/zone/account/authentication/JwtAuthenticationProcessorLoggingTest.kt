package net.bestia.zone.account.authentication

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import net.bestia.account.Role
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.Date
import kotlin.test.assertTrue

/**
 * A login token is a bearer credential for two minutes. Whoever reads the log in that time can take over the
 * session, so no log level may print it.
 */
class JwtAuthenticationProcessorLoggingTest {

  private val logger = LoggerFactory.getLogger(JwtAuthenticationProcessor::class.java) as Logger
  private val appender = ListAppender<ILoggingEvent>()
  private var previousLevel: Level? = null

  @BeforeEach
  fun captureEverything() {
    previousLevel = logger.level
    logger.level = Level.TRACE
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun restore() {
    logger.detachAppender(appender)
    logger.level = previousLevel
  }

  @Test
  fun `the login token is never logged`() {
    val validator = mockk<LoginTokenValidator> {
      every { validateLoginToken(TOKEN) } returns LoginTokenValidator.LoginTokenClaims(1L, Role.USER, emptySet(), Date())
    }

    JwtAuthenticationProcessor(validator, mockk(relaxed = true)).authenticate(authenticationEnvelope())

    assertTrue(appender.list.none { TOKEN in it.formattedMessage }, "logged: ${appender.list.map { it.formattedMessage }}")
  }

  private fun authenticationEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken(TOKEN))
      .build()
  }

  private companion object {
    const val TOKEN = "secret-token-value"
  }
}
