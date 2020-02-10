package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.geometry.Rect
import net.bestia.zoneserver.entity.component.PositionComponent
import kotlin.math.max
import kotlin.math.min

private val LOG = KotlinLogging.logger { }

class LineOfSightAttackCheck() : AttackCheck() {
  override fun checkAttackCondition(): Boolean {
    LOG.warn { "Line Of Sight check not implemented" }
    return true
  }

  /**
   * Checks if there is a direct line of sight between the two points. This
   * does not only take static map features into account but also dynamic
   * effects like entities which might block the direct line of sight.
   *
   * @return Returns TRUE if there is a direct line of sight. FALSE if there
   * is no direct line of sight.
   */
  private fun hasLineOfSight(battleCtx: BattleContext): Boolean {
    val attack = battleCtx.usedAttack
    val attacker = battleCtx.attacker
    val defender = battleCtx.defender

    if (!attack.needsLineOfSight) {
      LOG.trace("Attack does not need los.")
      return true
    }

    val start = attacker.getComponent(PositionComponent::class.java).position
    val end = defender.getComponent(PositionComponent::class.java).position

    val x1 = min(start.x, end.x)
    val x2 = max(start.x, end.x)
    val y1 = min(start.y, end.y)
    val y2 = max(start.y, end.y)
    val z1 = min(start.z, end.z)
    val z2 = max(start.z, end.z)

    val width = x2 - x1
    val depth = y2 - y1
    val height = z2 - z1

    val bbox = Rect(x1, y1, z1, width, depth, height)

    return true
    /*
    val map = mapService.getMap(bbox)

    val lineOfSight = lineOfSight(start, end)
    val doesMapBlock = lineOfSight.any { map.blocksSight(it) }
    val doesEntityBlock = entityCollisionService.getAllCollidingEntityIds(lineOfSight).isNotEmpty()

    val hasLos = !doesMapBlock && !doesEntityBlock
    LOG.trace("Entity has line of sight: {}", hasLos)
    return hasLos*/
  }
}