package net.bestia.zone.battle.attack

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.scripts.AuraScript
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import java.util.Random
import org.springframework.stereotype.Component

/**
 * The two weapon boss essences that act on a landed swing: Reaping Scythe strikes every foe next to the target with
 * the same blow, and Doom may drop a target that is no boss to 1 HP.
 */
@Component
class EssenceSwings(
  private val aoi: EntityAOIService,
  private val random: Random = ThreadLocalRandomSource,
) : SwingProc {

  override fun onSwingLanded(world: World, attackerId: EntityId, targetId: EntityId, swing: Damage) {
    val values = world.get(attackerId, CombatStats::class)?.values ?: return
    val melee = values.weapon.ranged == null

    if (values.meleeSplash && melee) splash(world, attackerId, targetId, swing.amount)
    if (values.doomChancePercent > 0 && random.nextInt(100) < values.doomChancePercent) doom(world, attackerId, targetId)
  }

  private fun splash(world: World, attackerId: EntityId, targetId: EntityId, amount: Int) {
    val centre = world.get(targetId, Position::class)?.toVec3L() ?: return

    for (id in aoi.queryEntitiesInCube(centre, SPLASH_CUBE, AoiLayer.DYNAMIC_ONLY)) {
      if (id == targetId || id == attackerId || world.has(id, Dead::class) || !world.has(id, Health::class)) continue
      val at = world.get(id, Position::class)?.toVec3L() ?: continue
      val dx = at.x - centre.x
      val dy = at.y - centre.y
      val nextToTarget = dx * dx + dy * dy <= SPLASH_SQUARED_METRES
      val struck = nextToTarget && AuraScript.isFoeOf(world, attackerId, id) &&
        DamageGate.verdict(world, attackerId, id) == DamageGate.Verdict.ADMITTED
      if (!struck) continue

      val staged = world.get(id, IncomingDamage::class) ?: world.add(id, IncomingDamage())
      staged.add(amount, attackerId, HitKind.physical(HitReach.MELEE))
    }
  }

  /** Staged like any blow, so the death, the threat and the experience follow the usual way. */
  private fun doom(world: World, attackerId: EntityId, targetId: EntityId) {
    if (world.get(targetId, Nature::class)?.boss == true) return
    val health = world.get(targetId, Health::class) ?: return
    val toOneHp = health.current - 1
    if (toOneHp <= 0) return

    val staged = world.get(targetId, IncomingDamage::class) ?: world.add(targetId, IncomingDamage())
    staged.add(toOneHp, attackerId)
  }

  private companion object {
    /** 1.5 m around the target, in whole metres: the eight places next to it. */
    const val SPLASH_SQUARED_METRES = 2L
    const val SPLASH_CUBE = 4L
  }
}
