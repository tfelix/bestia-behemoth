package net.bestia.zone.settlement.ecs

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.settlement.TownGrowth
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.structure.StructureCollision
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component as SpringComponent

/**
 * A building that only works in a town decays while no standing town holds it: from full health it breaks in
 * 96 h. See the design docs' player settlements page.
 */
@SpringComponent
class TownDecaySystem(
  private val towns: TownRegistry,
  private val structures: PlayerStructureRegistry,
  private val propKinds: PropKindRegistry,
  private val residency: WorldObjectResidencyService,
  private val collision: StructureCollision,
  private val worldService: WorldService,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(RUN_SECONDS)
  override val phase = Phase.UPKEEP
  override val after = setOf(TownUpkeepSystem::class)

  override val reads: ComponentClassSet = setOf(PlayerStructureIdentity::class)
  override val writes: ComponentClassSet = setOf(Health::class)

  /** Decay that has not added up to a whole hit point yet, per structure. */
  private val owed = HashMap<Long, Double>()

  override fun update(world: World, deltaTime: Float) {
    val decaying = structures.all().filter(::isDecaying)
    if (decaying.isEmpty()) return

    val entityOf = HashMap<Long, EntityId>()
    world.each(PlayerStructureIdentity::class) { id, identity -> entityOf[identity.structureId] = id }

    for (entry in decaying) {
      val maxHp = propKinds.of(entry.kind).maxHp
      val loss = takeWholeLoss(entry.id, maxHp * deltaTime / DECAY_SECONDS)
      if (loss == 0) continue

      val health = entityOf[entry.id]?.let { world.get(it, Health::class) }
      val missingHp = (health?.let { it.max - it.current } ?: entry.missingHp) + loss
      when {
        missingHp >= maxHp -> breakDown(world, entry, entityOf[entry.id])
        health != null -> health.current -= loss
        else -> structures.setWear(entry.id, missingHp, entry.repairStockHp)
      }
    }
  }

  private fun isDecaying(entry: StructureEntry): Boolean {
    return entry.kind in TownGrowth.FUNCTIONAL_KINDS && !entry.isUnderConstruction && towns.standingAt(entry.position) == null
  }

  private fun takeWholeLoss(structureId: Long, share: Double): Int {
    val total = owed.getOrDefault(structureId, 0.0) + share
    val whole = total.toInt()
    owed[structureId] = total - whole

    return whole
  }

  /** Decay is not a fight, so nothing dies: the building is simply taken out of the world. */
  private fun breakDown(world: World, entry: StructureEntry, residentId: EntityId?) {
    residentId?.let { residency.remove(world, it) }
    val chunkSize = worldService.config.chunkSize.toLong()
    structures.remove(entry.id, Math.floorDiv(entry.position.x, chunkSize).toInt(), Math.floorDiv(entry.position.y, chunkSize).toInt())
    collision.fall(entry.id)
    owed.remove(entry.id)
  }

  private companion object {
    const val RUN_SECONDS = 60f
    const val DECAY_SECONDS = 96 * 3600.0
  }
}
