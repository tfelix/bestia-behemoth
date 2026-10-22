package net.bestia.zone.guild.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * What the other members of a master's guild see of it from afar: where it is and how its master fares. On
 * every master; [GuildMemberBeaconSystem] keeps it current while the master is in a guild and played.
 *
 * Coarser than [net.bestia.zone.movement.ecs.Position] on purpose: a whole guild hears it at any distance, so a
 * step does not count as news until it adds up to [MOVE_THRESHOLD_METRES].
 */
class GuildMemberBeacon : DirtyableComponent() {

  var position: Vec3L = Vec3L.ZERO
    private set
  var areaName: String = ""
    private set
  var vitals: Vitals = Vitals(0, 0, 0, 0)
    private set
  private var placed = false

  data class Vitals(val hpCurrent: Int, val hpMax: Int, val manaCurrent: Int, val manaMax: Int)

  /** [areaOf] is only asked when the beacon moves. */
  fun update(position: Vec3L, vitals: Vitals, areaOf: (Vec3L) -> String) {
    if (hasMovedFarFrom(position)) {
      this.position = position
      areaName = areaOf(position)
      placed = true
      markDirty()
    }

    if (vitals != this.vitals) {
      this.vitals = vitals
      markDirty()
    }
  }

  private fun hasMovedFarFrom(next: Vec3L): Boolean {
    val dx = next.x - position.x
    val dy = next.y - position.y

    return !placed || dx * dx + dy * dy >= MOVE_THRESHOLD_METRES * MOVE_THRESHOLD_METRES
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return GuildMemberBeaconComponentSMSG(
      entityId = entityId,
      position = position,
      areaName = areaName,
      hpCurrent = vitals.hpCurrent,
      hpMax = vitals.hpMax,
      manaCurrent = vitals.manaCurrent,
      manaMax = vitals.manaMax,
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerAndGuild
  }

  companion object {
    const val MOVE_THRESHOLD_METRES = 4L
  }
}
