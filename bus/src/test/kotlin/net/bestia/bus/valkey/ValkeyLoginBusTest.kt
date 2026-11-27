package net.bestia.bus.valkey

import io.lettuce.core.RedisClient
import net.bestia.bus.LoginCommandHandler
import net.bestia.bus.LoginUnavailableException
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Testcontainers
class ValkeyLoginBusTest {

  private val ban = LoginCommand.Ban(targetAccountId = 9, note = "botting", durationHours = 24)
  private val closeables = mutableListOf<AutoCloseable>()

  @AfterEach
  fun closeAll() {
    closeables.asReversed().forEach { it.close() }
  }

  @Test
  fun `a command reaches the login server with its actor and the outcome comes back`() {
    val received = CopyOnWriteArrayList<Pair<Long, LoginCommand>>()
    listening { actor, command ->
      received += actor to command
      LoginReply.Outcome.NOT_ALLOWED
    }

    val outcome = bus("i1").send(7, ban).get(5, TimeUnit.SECONDS)

    assertEquals(LoginReply.Outcome.NOT_ALLOWED, outcome)
    assertEquals(listOf<Pair<Long, LoginCommand>>(7L to ban), received.toList())
  }

  @Test
  fun `a command is carried out once however many login servers listen`() {
    val runs = AtomicInteger()
    repeat(3) {
      listening { _, _ ->
        runs.incrementAndGet()
        LoginReply.Outcome.DONE
      }
    }

    assertEquals(LoginReply.Outcome.DONE, bus("i2").send(7, ban).get(5, TimeUnit.SECONDS))
    Thread.sleep(300)
    assertEquals(1, runs.get())
  }

  @Test
  fun `a handler that throws is answered failed`() {
    listening { _, _ -> error("database gone") }

    assertEquals(LoginReply.Outcome.FAILED, bus("i3").send(7, ban).get(5, TimeUnit.SECONDS))
  }

  @Test
  fun `a login server that never answers times out`() {
    val release = CountDownLatch(1)
    listening { _, _ ->
      release.await()
      LoginReply.Outcome.DONE
    }

    val failure = assertThrows<ExecutionException> {
      bus("i4").send(7, ban, timeout = Duration.ofMillis(300)).get(5, TimeUnit.SECONDS)
    }

    assertIs<TimeoutException>(failure.cause)
    release.countDown()
  }

  @Test
  fun `sending with no login server listening fails at once`() {
    val failure = assertThrows<ExecutionException> { bus("i5").send(7, ban).get(5, TimeUnit.SECONDS) }

    assertIs<LoginUnavailableException>(failure.cause)
  }

  @Test
  fun `a login server starts while Valkey is down and closes cleanly`() {
    val unreachable = RedisClient.create("redis://127.0.0.1:1")
    val server = ValkeyLoginCommandServer(unreachable, { _, _ -> LoginReply.Outcome.DONE }, Duration.ofMillis(50))

    Thread.sleep(200)
    assertTrue(!server.isListening)
    server.close()
    unreachable.shutdown()
  }

  private fun listening(handler: LoginCommandHandler) {
    val server = ValkeyLoginCommandServer(client, handler)
    closeables += server
    await().atMost(Duration.ofSeconds(10)).until { server.isListening }
  }

  private fun bus(instanceId: String): ValkeyLoginBus {
    return ValkeyLoginBus(client, instanceId).also { closeables += it }
  }

  companion object {
    @Container
    @JvmStatic
    val valkey: GenericContainer<*> = GenericContainer("valkey/valkey:8.1").withExposedPorts(6379)

    private lateinit var client: RedisClient

    @BeforeAll
    @JvmStatic
    fun connect() {
      client = RedisClient.create("redis://${valkey.host}:${valkey.getMappedPort(6379)}")
    }

    @AfterAll
    @JvmStatic
    fun disconnect() {
      client.shutdown()
    }
  }
}
