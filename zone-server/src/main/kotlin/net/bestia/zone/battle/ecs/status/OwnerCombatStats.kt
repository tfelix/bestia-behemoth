package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * What the owner's status window shows of its [CombatStats], held by reference so it cannot differ from what a swing
 * spends. Only masters and owned bestias carry one, which is how the recalc knows to keep it current; an equal block
 * sends nothing.
 *
 * ASPD is derived here, not in the block, because it depends on the swing: a player's is the bare-handed one
 * `AttackSystem` makes, so [aspd] has to follow when that starts reading the weapon.
 */
class OwnerCombatStats(values: CombatValues) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var values: CombatValues = values
    private set

  val aspd: Int
    get() {
      return AttackSpeed.aspd(AttackSpeed.motionMs(AttackSpeed.BARE_HANDED_MOTION_MS, values.attributes))
    }

  private val ranged: RangedWeapon?
    get() {
      return values.weapon.ranged
    }

  fun update(resolved: CombatValues) {
    if (resolved == values) {
      return
    }

    values = resolved
    markDirty()
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return CombatStatsComponentSMSG(
      entityId = entityId,
      // The ATK a basic attack spends: a bow shoots with the DEX-heavy one.
      atk = if (ranged == null) values.derived.atk else values.derived.rangedAtk,
      weaponAtk = values.weapon.physicalAtk,
      matk = values.derived.matk,
      weaponMatk = values.weapon.matk,
      hit = values.derived.hitrate,
      flee = values.derived.flee,
      crit = values.derived.crit,
      softDefense = values.defense.defense,
      hardDefense = values.defense.hardDefense,
      aspd = aspd,
      attackRange = ranged?.range?.toInt() ?: BattleAttack.MELEE_REACH.toInt(),
      softMagicDefense = values.defense.magicDefense,
      hardMagicDefense = values.defense.hardMagicDefense
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
