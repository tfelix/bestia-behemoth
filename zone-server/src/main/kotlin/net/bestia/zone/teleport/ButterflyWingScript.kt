package net.bestia.zone.teleport

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.SavePointService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `items.yml` id 75, Butterfly Wing: home to the user's save point. Far, so the client shows a loading screen.
 *
 * The save point is in the database, which the tick may not read. It is looked up while the body vanishes and
 * posted back; [TeleportSystem] waits for it if the vanish ends first.
 */
@Component
class ButterflyWingScript(
  statusEffects: StatusEffectService,
  out: OutMessageProcessor,
  private val settings: TeleportItemSettings,
  private val savePoints: SavePointService,
  private val asyncJobs: AsyncJobExecutor,
  private val worldView: WorldView,
) : WingScript(statusEffects, out) {

  override val itemId: Long = 75L

  override val vanish = EntityFx.Kind.HOME_VANISH
  override val vanishMs: Int get() = settings.butterflyWingVanishMs

  override fun destination(world: World, userId: EntityId): Teleporting.Destination {
    val home = Teleporting.Destination.Home()
    val bestia = world.get(userId, OwnedBestia::class)
    val masterId = world.get(userId, Master::class)?.masterId

    asyncJobs.submit(key = userId) {
      val savePoint = runCatching {
        when {
          bestia != null -> savePoints.forPlayerBestia(bestia.playerBestiaId)
          masterId != null -> savePoints.forMaster(masterId)
          else -> error("entity $userId is neither a master nor a player bestia")
        }
      }.onFailure { LOG.warn(it) { "No save point for entity $userId; the butterfly wing fizzles" } }

      worldView.post {
        home.savePoint = savePoint.getOrNull()
        home.lookupFailed = savePoint.isFailure
      }
    }

    return home
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
