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
import kotlin.math.floor
import kotlin.random.Random
import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.identity.ecs.Account

/**
 * Records what a promoted prop's death means for the durable object it was, once per kill, ever: its loot,
 * and through [PropRemoval] what it leaves behind. A felled or burnt tree leaves its stump.
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
  private val removal: PropRemoval,
) : System {
  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  // Either order is right: that one settles player-built structures and this one generated props. Stated only
  // because a stump this leaves behind is a prop entity, and the two touch the same prop components.
  override val after = setOf(PlayerStructureDeathSystem::class)

  override val reads: ComponentClassSet =
    setOf(
      Dead::class, WorldObjectIdentity::class, StaticVisual::class, Position::class, TakenDamage::class,
      Account::class
    )

  // The loot entity is brand new and needs nothing declared - DeathSystem's own spawnLoot has the same shape.
  // The stump a tree leaves is a prop entity, and the world refuses its components unless they are declared.
  override val writes: ComponentClassSet = PropRemoval.WRITES

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, WorldObjectIdentity::class, StaticVisual::class).each { id ->
      val identity = get<WorldObjectIdentity>()
      val visual = get<StaticVisual>()

      // A prop now has two ways to be used up, and whichever records the divergence first wins. Without this,
      // a crystal collected by one player at order 64 and finished off by another's in-flight damage in the
      // same tick would yield twice - once into an inventory, once onto the ground.
      //
      // A tree growing back has a row and still stands, so a sapling or a young tree can be felled again. A
      // stump cannot: it is never promoted, so it never dies.
      val standing = divergence.of(identity.propId)
      if (standing != null && !standing.isFellable) return@each

      val position = world.get(id, Position::class)?.toVec3L()

      val spec = kinds.of(visual.kind)
      val yieldShare = spec.regrowth?.yieldOf(visual.growth) ?: 1.0
      if (position != null) {
        val killer = world.get(id, TakenDamage::class)?.topAccount(world)
        spec.loot.forEach { entry ->
          val amount = floor(entry.amount * yieldShare).toInt()
          if (amount > 0 && Random.nextInt(1, 10_001) <= entry.dropChance) {
            lootItemEntitySpawner.spawnLootItem(
              world, itemId = entry.itemId, amount = amount, pos = position, lootOwner = killer
            )
          }
        }
      }

      removal.removed(world, id, PropRemoval.Cause.USED_UP)
    }
  }
}
