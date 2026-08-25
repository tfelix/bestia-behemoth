package net.bestia.zone.world.prop

import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import java.time.Instant
import kotlin.random.Random

/**
 * Records what a promoted prop's death means for the durable object it was, once per kill, ever.
 *
 * `@Order(65)`: after [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem] (`@Order(50)`, adds [Dead])
 * and before [net.bestia.zone.ecs.battle.damage.DeathSystem] (`@Order(70)`, unconditionally destroys
 * anything `Dead` - its own `assignExp`/`spawnLoot` already no-op harmlessly here since a prop has no
 * `EntityVisual`, so that system needs no changes at all). No wave-scheduling conflict: neither system reads
 * or writes what the other does.
 *
 * ### Exactly once, by construction, not by locking
 *
 * Two simultaneous attackers finishing the same prop in the same tick still only ever produce one `Dead` -
 * `SkillExecutionService.applyResult` stages both hits onto the *same* `DamageComponent` instance per
 * target, and `ReceivedDamageSystem` drains it once per tick, adding `Dead` at most once. This system's own
 * query over `Dead` therefore sees a given propId's death exactly once, ever: the entity is destroyed the
 * same tick, later in `@Order`, so it can never reappear in a future tick's query.
 */
@SpringComponent
@Order(65)
class PropDeathDivergenceSystem(
  private val kinds: PropKindRegistry,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val divergence: WorldObjectDivergenceRegistry,
) : System {

  override val reads: ComponentClassSet =
    setOf(Dead::class, WorldObjectIdentity::class, StaticVisual::class, Position::class)

  // Empty: recordDepletion only touches WorldObjectDivergenceRegistry's own map (off the ECS entirely), and
  // the loot entity it may create is brand new - DeathSystem's own spawnLoot demonstrates the same shape
  // needs no writes declared, since a freshly created id was not there for any other system to conflict on.
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, WorldObjectIdentity::class, StaticVisual::class).each { id ->
      val identity = get<WorldObjectIdentity>()
      val visual = get<StaticVisual>()

      // A prop now has two ways to be used up, and whichever records the divergence first wins. Without this,
      // a crystal collected by one player at order 64 and finished off by another's in-flight damage in the
      // same tick would yield twice - once into an inventory, once onto the ground.
      //
      // Inert for anything that only ever dies: a standing prop has no divergence (a felled one is destroyed
      // the same tick at order 70, and a regrown one was evicted by `shouldEmit` when its column reloaded).
      if (divergence.of(identity.propId) != null) return@each

      val position = world.get(id, Position::class)?.toVec3L()

      val spec = kinds.of(visual.kind)
      if (position != null) {
        spec.loot.forEach { entry ->
          if (Random.nextInt(1, 10_001) <= entry.dropChance) {
            lootItemEntitySpawner.spawnLootItem(world, itemId = entry.itemId, amount = entry.amount, pos = position)
          }
        }
      }

      val resumeAt = spec.regrowSeconds?.let { Instant.now().plusSeconds(it) }
      divergence.recordDepletion(identity.propId, visual.kind, resumeAt)
    }
  }
}
