package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Dissonant Chord (`skills.yml` id 164): with an instrument, puts [StatusEffectId.DISSONANCE] at the skill's level on
 * every foe within 3 tiles of the Bard.
 */
@Component
class DissonantChord : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return BardWeapon.INSTRUMENT.refusalFor(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return BardWeapon.INSTRUMENT.isHeld(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val bardAt = ctx.world.positionOf(ctx.casterId) ?: return null
    val foes = ctx.world.entitiesInCube(bardAt, REACH_TILES * 2).filter { ctx.world.isFoe(it) }
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    foes.forEach { ctx.world.applyStatusEffect(it, StatusEffectId.DISSONANCE.id, level) }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val REACH_TILES = 3L
  }
}
