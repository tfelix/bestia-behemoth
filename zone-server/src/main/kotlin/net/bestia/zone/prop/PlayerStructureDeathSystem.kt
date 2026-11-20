package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.chest.ChestStorages
import net.bestia.zone.structure.StructureCollision
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component as SpringComponent
import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem

/**
 * Forgets a player-built station that has been knocked down.
 *
 * Covers both shapes a structure comes in - the static prop a finished one is, and the ordinary entity a
 * construction site is.
 *
 * The counterpart to [PropDeathDivergenceSystem], and separate from it because the two record opposite things.
 * A felled tree writes a *divergence* row saying the generator's output no longer applies here; a broken forge
 * has no generator output to diverge from, so its own row is simply deleted and there is nothing left to say.
 *
 * In the death phase: after `ReceivedDamageSystem` has added [Dead] and before `DeathSystem` destroys the
 * entity, alongside the divergence system. Nothing about the two conflicts, so their relative order does not
 * matter - only that both run inside that window.
 */
@SpringComponent
class PlayerStructureDeathSystem(
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
  private val collision: StructureCollision,
  private val spills: List<StationSpill>,
  private val chests: ChestStorages,
  private val events: ApplicationEventPublisher,
  private val salvage: StructureSalvage,
) : System {
  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  override val reads: ComponentClassSet =
    setOf(Dead::class, PlayerStructureIdentity::class, PropPose::class, Position::class, TakenDamage::class,
      Account::class) + salvage.reads

  // Salvage only: everything else this does is to the registry's own map, which is off the ECS entirely.
  override val writes: ComponentClassSet = salvage.writes

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, PlayerStructureIdentity::class).each { id ->
      val identity = get<PlayerStructureIdentity>()

      // Either, because a structure is one of two quite different things depending on whether it is finished:
      // a static prop, which is not in the `Position` store at all, or a construction site, which is an
      // ordinary entity and has no `PropPose`. Both are knocked down the same way and both own a row.
      val position = world.get(id, PropPose::class)?.position ?: world.get(id, Position::class)?.toVec3L()
      if (position == null) {
        LOG.warn { "Player structure ${identity.structureId} died with no position; its row is left behind" }
        return@each
      }

      val entry = structures.of(identity.structureId)
      entry?.let { found -> spills.forEach { it.spill(world, found, position) } }
      chests.spill(identity.structureId, identity.ownerAccountId, position)
      entry?.let { found -> salvageFor(world, id, found, position) }

      val chunkSize = worldService.config.chunkSize.toLong()
      structures.remove(
        structureId = identity.structureId,
        chunkX = Math.floorDiv(position.x, chunkSize).toInt(),
        chunkY = Math.floorDiv(position.y, chunkSize).toInt()
      )
      collision.fall(identity.structureId)
      if (entry != null) {
        events.publishEvent(PlayerStructureDestroyedEvent(this, entry.id, entry.kind, position))
      }

      LOG.info { "Player structure ${identity.structureId} was destroyed at $position" }
    }
  }

  /** Only another master earns it, and only for a finished structure. */
  private fun salvageFor(world: World, structureEntityId: EntityId, structure: StructureEntry, at: Vec3L) {
    if (structure.isUnderConstruction) return

    val damage = world.get(structureEntityId, TakenDamage::class) ?: return
    val salvagerAccountId = damage.topAccount(world) ?: return
    val salvagerId = damage.topSource() ?: return
    if (salvagerAccountId == structure.ownerAccountId) return

    salvage.salvage(world, structure, at, salvagerId, salvagerAccountId)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
