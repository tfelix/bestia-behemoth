package net.bestia.zone.prop

import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.springframework.stereotype.Component as SpringComponent
import java.time.Instant
import kotlin.random.Random
import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.identity.ecs.Account

/**
 * Records what a promoted prop's death means for the durable object it was, once per kill, ever.
 *
 * In the death phase: after [net.bestia.zone.battle.ecs.damage.ReceivedDamageSystem] (adds [Dead]) and before
 * [net.bestia.zone.spawn.ecs.DeathSystem] (unconditionally destroys anything `Dead` - its own `assignExp`/`spawnLoot` already no-op harmlessly here since a prop has no
 * `EntityVisual`, so that system needs no changes at all). No wave-scheduling conflict: neither system reads
 * or writes what the other does.
 *
 * ### Exactly once, by construction, not by locking
 *
 * Two simultaneous attackers finishing the same prop in the same tick still only ever produce one `Dead` -
 * `SkillExecutionService.applyResult` stages both hits onto the *same* `IncomingDamage` instance per
 * target, and `ReceivedDamageSystem` drains it once per tick, adding `Dead` at most once. This system's own
 * query over `Dead` therefore sees a given propId's death exactly once, ever: the entity is destroyed the
 * same tick, later in the death phase, so it can never reappear in a future tick's query.
 */
@SpringComponent
class PropDeathDivergenceSystem(
  private val kinds: PropKindRegistry,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val divergence: WorldObjectDivergenceRegistry,
) : System {
  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  override val reads: ComponentClassSet =
    setOf(
      Dead::class, WorldObjectIdentity::class, StaticVisual::class, Position::class, TakenDamage::class,
      Account::class
    )

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
        val killer = world.get(id, TakenDamage::class)?.topAccount(world)
        spec.loot.forEach { entry ->
          if (Random.nextInt(1, 10_001) <= entry.dropChance) {
            lootItemEntitySpawner.spawnLootItem(
              world, itemId = entry.itemId, amount = entry.amount, pos = position, lootOwner = killer
            )
          }
        }
      }

      val resumeAt = spec.regrowSeconds?.let { Instant.now().plusSeconds(it) }
      divergence.recordDepletion(identity.propId, visual.kind, resumeAt)
    }
  }
}
