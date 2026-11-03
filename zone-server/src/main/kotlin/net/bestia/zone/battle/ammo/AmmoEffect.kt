package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What special ammo does beyond its attack and element, named by the ammo's `script` in `items.yml`. Armor
 * piercing lowers `damageVariables.physicalDefenseMod` in [beforeHit]; a stun arrow applies its effect in [onHit].
 */
interface AmmoEffect {

  /** Before the damage roll, once the arrow is spent. */
  fun beforeHit(ctx: BattleContext) {}

  /** After a hit that dealt [damage]. Not called for a miss. */
  fun onHit(world: World, attackerId: EntityId, targetId: EntityId, damage: Damage) {}

  companion object {
    /** A plain arrow: its attack and element are all it brings. */
    val NONE = object : AmmoEffect {}
  }
}
