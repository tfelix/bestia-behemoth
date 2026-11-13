package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.ScrySight
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.casting.portal.WarpMarks
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Warp Portal (`skills.yml` id 72). `args[0]` picks what the cast does:
 * - [MARK]: saves the spot the Sage stands on, or looks at through Scry, at once and for free. One spot per level,
 *   the oldest makes room.
 * - [HOME]: opens a portal to the Sage's home.
 * - [FIRST_SPOT] + i: opens a portal to the i-th saved spot, oldest first.
 *
 * A portal stands for 30 s and lets 2 bodies through per level, either way. The Portal Rune is the skill's reagent.
 */
@Component
class WarpPortal : SkillStrategy {

  override fun freeAction(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): Boolean {
    if (args.firstOrNull() != MARK) return false
    // A Sage whose Scry is open marks the spot it looks at, which is how a portal's far end is scouted.
    val at = world.get(casterId, ScrySight::class)?.centre ?: world.get(casterId, Position::class)?.toVec3L() ?: return true
    val marks = world.get(casterId, WarpMarks::class) ?: world.add(casterId, WarpMarks())
    marks.mark(at, capacity = skillLevel.coerceIn(1, MAX_LEVEL))

    return true
  }

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val choice = args.firstOrNull() ?: return OpError.SKILL_INVALID_CHOICE
    if (choice == HOME) return null
    if (choice < FIRST_SPOT) return OpError.SKILL_INVALID_CHOICE

    return if (spotOf(world.get(casterId, WarpMarks::class), choice) == null) OpError.PORTAL_NO_SPOT else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.targetPosition != null

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.targetPosition ?: return null
    val choice = ctx.args.firstOrNull() ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    val to = if (choice == HOME) null else ctx.world.warpMarkOf(ctx.casterId, (choice - FIRST_SPOT).toInt()) ?: return null
    ctx.world.openPortal(at, to, passages = PASSAGES_PER_LEVEL * level, seconds = OPEN_SECONDS)

    return null
  }

  companion object {
    const val MARK = 0L
    const val HOME = 1L
    const val FIRST_SPOT = 2L

    private const val MAX_LEVEL = 5
    private const val PASSAGES_PER_LEVEL = 2
    private const val OPEN_SECONDS = 30

    private fun spotOf(marks: WarpMarks?, choice: Long) = marks?.spots?.getOrNull((choice - FIRST_SPOT).toInt())
  }
}
