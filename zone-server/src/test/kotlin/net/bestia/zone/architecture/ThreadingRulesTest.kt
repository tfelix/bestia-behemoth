package net.bestia.zone.architecture

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.test.assertEquals

/**
 * Pins the threads the zone runs on. Anything else that needs time runs on one of them: on the tick
 * (`World.post`), on an account's inbox, as a DB job, or from a `@Scheduled` sweep.
 */
class ThreadingRulesTest {

  /** The only places allowed to start threads, each with what it runs. */
  private val threadOwners = mapOf(
    "ecs/ZoneEngine.kt" to "the tick thread, which owns the world",
    "ecs/core/SystemScheduler.kt" to "parallel waves, which run while the tick thread waits",
    "message/AccountInbox.kt" to "the IO lane for handlers that need the database",
    "ecs/core/AsyncJobExecutor.kt" to "database writes, ordered per owner",
    "world/stream/ChunkWorkers.kt" to "terrain encoding, which never touches the world",
    "socket/SocketServer.kt" to "the Netty server",
    "cartography/tile/TileRenderPool.kt" to "map tile rendering, which never touches the world",
  )

  private val startsThreads = Regex("""Executors\.|ScheduledExecutorService|ForkJoinPool\(|[^\w.]Thread\(|@Async|\bthread\(""")

  @Test
  fun `only the known thread owners start threads`() {
    val root = Path.of("src/main/kotlin/net/bestia/zone")

    val offenders = Files.walk(root).use { paths ->
      paths
        .filter { it.extension == "kt" }
        .filter { file -> file.readText().lineSequence().any { line -> isCode(line) && startsThreads.containsMatchIn(line) } }
        .map { it.relativeTo(root).invariantSeparatorsPathString }
        .filter { it !in threadOwners }
        .toList()
    }

    assertEquals(emptyList(), offenders)
  }

  private fun isCode(line: String): Boolean {
    val trimmed = line.trimStart()
    return !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
  }
}
