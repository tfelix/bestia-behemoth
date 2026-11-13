package net.bestia.zone.spellbinding

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.ecs.construction.ConstructionSystem
import net.bestia.zone.respawn.ecs.RespawnSystem
import net.bestia.zone.teleport.TeleportSystem
import net.bestia.zone.util.EntityId
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Watches every ground binding for its trigger. Fires between ticks, where casting and creating entities are safe,
 * and so a binding found twice before then still fires once.
 */
@SpringComponent
class SpellBindingSystem(
  private val aoi: EntityAOIService,
  private val book: SpellBindingBook,
  private val firing: SpellBindingFiring,
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.ACTIONS

  /** Looks where creatures stand once they arrived, rose or were built this tick. */
  override val after = setOf(RespawnSystem::class, ConstructionSystem::class, TeleportSystem::class)
  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(GroundSpellBinding::class, Position::class) + BindingTargets.READS

  private class Fired(val bound: BoundSpell, val at: Vec3L, val target: EntityId?)

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()
    val fired = mutableListOf<Fired>()

    world.query(GroundSpellBinding::class, Position::class).each { _ ->
      val bound = get<GroundSpellBinding>().bound
      val at = get<Position>().toVec3L()
      val targets = BindingTargets(world, bound.owner)

      when (bound.trigger) {
        BindingTrigger.STEPS_ON -> creatureOnTile(world, at, bound, targets)?.let { fired += Fired(bound, at, it) }
        BindingTrigger.COMES_NEAR -> nearestCreature(world, at, bound, targets)?.let { fired += Fired(bound, at, it) }
        BindingTrigger.AFTER_DELAY -> if (bound.firesAt?.isAfter(now) == false) {
          fired += Fired(bound, at, nearestCreature(world, at, bound, targets))
        }

        else -> Unit
      }
    }

    if (fired.isEmpty() && !firing.hasLingeringCasters()) {
      return
    }
    world.post {
      firing.removeGoneCasters(this)
      fired.forEach { fire(this, it) }
    }
  }

  private fun fire(world: World, fired: Fired) {
    val entry = book.find(fired.bound.owner.masterId, fired.bound.id) ?: return
    firing.fire(world, entry, fired.at, fired.target)
  }

  private fun creatureOnTile(world: World, at: Vec3L, bound: BoundSpell, targets: BindingTargets): EntityId? {
    return aoi.queryEntitiesInCube(at, HEIGHT_TOLERANCE * 2, AoiLayer.DYNAMIC_ONLY)
      .filter { id ->
        val position = world.get(id, Position::class) ?: return@filter false
        position.x == at.x && position.y == at.y && targets.admits(bound.filter, id)
      }
      .minOrNull()
  }

  private fun nearestCreature(world: World, at: Vec3L, bound: BoundSpell, targets: BindingTargets): EntityId? {
    val radius = SpellBindingRules.NEAR_RADIUS_TILES

    return aoi.queryEntitiesInCube(at, radius * 2 + 1, AoiLayer.DYNAMIC_ONLY)
      .mapNotNull { id -> world.get(id, Position::class)?.toVec3L()?.let { id to it.distance(at) } }
      .filter { (id, distance) -> distance <= radius && targets.admits(bound.filter, id) }
      .minByOrNull { (id, distance) -> distance * 1_000_000 + id }
      ?.first
  }

  private companion object {
    /** A glyph lies at the aimed height, a walker stands on sampled ground: a couple of voxels either way. */
    const val HEIGHT_TOLERANCE = 2L
  }
}
