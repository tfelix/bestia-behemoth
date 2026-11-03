package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.battle.attack.BasicAttackResolver
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.item.ammo.QuiverAmmoSupply
import org.springframework.stereotype.Component

/**
 * Notices whether a player's bestia can shoot with what it holds, so it keeps its distance while the quiver
 * lasts and closes in with its own attack once it is empty. Wild mobs hold nothing and never learn the key.
 */
@Component
class HeldWeaponSense(
  private val basicAttacks: BasicAttackResolver,
) : Sense {

  override val name = "heldWeapon"

  override val intervalSeconds = 0.5f

  override val reads: ComponentClassSet = setOf(CombatStats::class) + QuiverAmmoSupply.READS + QuiverAmmoSupply.WRITES

  override fun sense(context: SenseContext) {
    // Only the reach is remembered: the leaf asks for the shot again when it fires, against the bag as it is then.
    val shot = basicAttacks.shotOf(context.world, context.entityId, AttackSpeed.BARE_HANDED_MOTION_MS)
    if (shot == null) {
      context.forget(BestiaDomain.SHOT_REACH)
    } else {
      context.remember(BestiaDomain.SHOT_REACH, shot.range)
    }
  }
}
