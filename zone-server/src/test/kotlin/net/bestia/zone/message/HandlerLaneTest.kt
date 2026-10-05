package net.bestia.zone.message

import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.Repository
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * A handler on the tick lane runs on the tick thread, where a database round trip stalls the world.
 * This catches the direct case at build time; `TickSqlGuard` catches the indirect ones at run time.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class HandlerLaneTest {

  @Autowired
  private lateinit var handlers: List<InMessageProcessor.IncomingMessageHandler<*>>

  @Test
  fun `no handler on the tick lane takes a repository or opens a transaction`() {
    val offenders = handlers
      .filter { it.lane == HandlerLane.TICK }
      .map { AopUtils.getTargetClass(it) }
      .filter { type ->
        val takesRepository = type.constructors.any { c ->
          c.parameterTypes.any { Repository::class.java.isAssignableFrom(it) }
        }
        val transactional = type.isAnnotationPresent(Transactional::class.java) ||
          type.methods.any { it.isAnnotationPresent(Transactional::class.java) }

        takesRepository || transactional
      }
      .map { it.simpleName }

    assertEquals(emptyList(), offenders)
  }
}
