package net.bestia.zone.item.ecs

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.AreaDamageReceiver
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.item.container.LooseInstanceDisposal
import net.bestia.zone.item.material.ItemMaterialRegistry
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The one way to damage a stack lying on the ground: area effects now, floods and the weather later.
 *
 * It adds no component until the stack is used up. So a system body, a deferred block or a background
 * simulation may call it, and a second hit in the same tick finds the stack already destroyed.
 */
@Component
class GroundItemDamage(
  private val itemTemplates: ItemTemplateRegistry,
  private val materials: ItemMaterialRegistry,
  private val groundStackRemoval: GroundStackRemoval,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val looseInstanceDisposal: LooseInstanceDisposal,
) : AreaDamageReceiver {

  /** For a calling system's own declarations. */
  override val reads: ComponentClassSet = setOf(GroundItemStack::class, Invulnerable::class)
  override val writes: ComponentClassSet = setOf(GroundItemIntegrity::class, Dead::class)

  override fun receive(world: World, victimId: EntityId, damage: Int, element: Element): Boolean {
    if (!world.has(victimId, GroundItemStack::class)) return false

    damage(world, victimId, damage, element)
    return true
  }

  fun damage(world: World, stackId: EntityId, amount: Int, element: Element) {
    if (world.has(stackId, Invulnerable::class)) return

    val stack = world.get(stackId, GroundItemStack::class) ?: return
    val integrity = world.get(stackId, GroundItemIntegrity::class) ?: return
    if (integrity.destroyed) return

    val material = itemTemplates.templateOf(stack.itemId)?.material ?: return
    val spec = materials.of(material)

    integrity.lost += spec.damageFrom(amount, element)
    if (integrity.lost >= spec.integrity) {
      destroy(world, stackId, stack, integrity)
    }
  }

  private fun destroy(world: World, stackId: EntityId, stack: GroundItemStack, integrity: GroundItemIntegrity) {
    integrity.destroyed = true
    // So the vanish tells clients the stack was destroyed rather than picked up.
    world.add(stackId, Dead())
    groundStackRemoval.remove(world, stackId)

    if (stack.uniqueId != 0L) {
      asyncJobExecutor.submit(stack.uniqueId) { looseInstanceDisposal.destroy(stack.uniqueId) }
    }
  }
}
