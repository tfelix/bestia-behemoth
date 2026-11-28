package net.bestia.zone.faction.warzone

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Closes war zones that went quiet and sends every master the zones it has not seen yet. */
@Component
class WarZoneSystem(
  private val warZones: WarZones,
  private val out: OutMessageProcessor,
) : System {
  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(5f)

  override val reads: ComponentClassSet = setOf(Account::class, Master::class)
  override val writes: ComponentClassSet = emptySet()

  /** The zone version each master entity was sent. A master that enters the world is a new entity, so it is sent the zones. */
  private val versionSent = HashMap<EntityId, Long>()

  override fun update(world: World, deltaTime: Float) {
    warZones.closeExpired()
    val version = warZones.version
    val message by lazy { WarZonesSMSG.of(warZones.open, warZones.voxelSize) }

    val online = HashSet<EntityId>()
    world.query(Master::class, Account::class).each { master ->
      online.add(master)
      if (versionSent.put(master, version) != version) {
        out.sendToPlayer(get<Account>().accountId, message)
      }
    }
    versionSent.keys.retainAll(online)
  }
}
