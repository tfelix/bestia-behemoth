package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts every player bestia back into the world, master online or not. A bestia never rests: it walks a journey
 * it was sent on before the restart, its idle AI runs, and the companion app can send it off without its master
 * logging in first. Its master is still loaded only on login.
 *
 * `@Order(114)`: after [PlayerBestiaEntityIdBootRunner] (109) so every row has its entity id, and after
 * [StatusEffectRestoreBootRunner] (111), because the spawner attaches a bestia's stored effects itself and that
 * pass would attach them a second time. Past the den and ward restores (112, 113) only to keep out of their
 * slots. Before the tick loop and the socket, like every entity restore.
 */
@Component
@Order(114)
class PlayerBestiaSpawnBootRunner(
  private val world: World,
  private val spawner: PlayerBestiaEntitySpawner,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val bestias = spawner.loadAll()
    spawner.spawnAtBoot(world, bestias)

    LOG.info { "Spawned ${bestias.size} player bestia(s) into the world." }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
