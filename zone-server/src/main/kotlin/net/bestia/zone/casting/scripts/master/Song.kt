package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * A Bard's song: with an instrument, starts [field] on the Bard at the skill's level. Playing it again refreshes it;
 * another song plays beside it. See [net.bestia.zone.battle.status.scripts.SongScript].
 */
abstract class Song(private val field: StatusEffectId) : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return BardWeapon.INSTRUMENT.refusalFor(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return BardWeapon.INSTRUMENT.isHeld(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, field.id, ctx.skillLevel)

    return null
  }
}
