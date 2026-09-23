package net.bestia.zone.item.script

import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * Food, drink and potions: everything whose whole effect is a number added to a vital.
 *
 * Still one class per item, because [ItemScript] binds by id - but the numbers are the only thing that
 * differs between a loaf and a wheel of cheese, so they are all a subclass names.
 *
 * A use succeeds when at least one named vital was actually moved. That is what lets a meal restoring both
 * feed a Master who has no [Health] at all, while a potion with nothing to heal stays in the bag.
 *
 * @param messages needed whenever [health] is restored: the number over the user's head is how a player
 *   sees a heal happen
 */
abstract class RestorativeScript(
  override val itemId: Long,
  private val health: Int = 0,
  private val stamina: Int = 0,
  private val mana: Int = 0,
  private val messages: OutMessageProcessor? = null,
) : ItemScript {

  init {
    require(health == 0 || messages != null) {
      "Item $itemId restores health but has nothing to show it with"
    }
  }

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val healed = restore(world.get(userId, Health::class), health)
    val fed = restore(world.get(userId, Stamina::class), stamina)
    val refilled = restore(world.get(userId, Mana::class), mana)

    if (!healed && !fed && !refilled) {
      return false
    }

    if (healed) {
      showHeal(world, userId)
    }

    return true
  }

  /** Whether the vital moved: one this item says nothing about, or one the user has not got, did not. */
  private fun restore(vital: CurMax?, amount: Int): Boolean {
    if (vital == null || amount == 0) {
      return false
    }

    // CurMax.current clamps to [0, max] itself, so using this while full simply wastes it.
    vital.current += amount

    return true
  }

  private fun showHeal(world: World, userId: EntityId) {
    val position = world.get(userId, Position::class) ?: return

    messages?.sendToAllPlayersInRange(
      position.toVec3L(),
      DamageEntitySMSG.fromItemHeal(userId, health)
    )
  }
}
