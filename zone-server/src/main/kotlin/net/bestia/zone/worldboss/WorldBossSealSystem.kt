package net.bestia.zone.worldboss

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropDeathDivergenceSystem
import net.bestia.zone.spawn.BestiaEntitySpawner
import org.springframework.stereotype.Component as SpringComponent

/**
 * Wakes the world boss bound in a seal crystal when the crystal breaks. The boss rises where the crystal stood.
 *
 * Once per world: the crystal is a terminal prop, so its divergence keeps it broken. The boss is persisted, so a
 * restart does not put it back in the crystal, and no den brings it back once it dies.
 *
 * Before [PropDeathDivergenceSystem], which removes the broken crystal.
 */
@SpringComponent
class WorldBossSealSystem(
  private val seals: WorldBossSealService,
  private val bestiaEntitySpawner: BestiaEntitySpawner,
) : System {

  override val phase = Phase.DEATH
  override val before = setOf(PropDeathDivergenceSystem::class)

  override val reads: ComponentClassSet =
    setOf(Dead::class, WorldObjectIdentity::class, StaticVisual::class, Position::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, WorldObjectIdentity::class, StaticVisual::class).each { id ->
      if (get<StaticVisual>().kind != StaticEntityKind.FIXTURE_SEAL_CRYSTAL) return@each

      val propId = get<WorldObjectIdentity>().propId
      val boss = seals.bossOf(propId) ?: return@each
      val at = world.get(id, Position::class)?.toVec3L() ?: return@each

      bestiaEntitySpawner.spawnMob(world, boss, at, persistent = true)
      LOG.info { "Seal crystal $propId broke at $at; world boss $boss is awake" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
