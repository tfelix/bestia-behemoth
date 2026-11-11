package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Teleport (`skills.yml` id 71): the Sage and its own bestias close by go to a random spot within 150 m at level 1,
 * home at level 2. The Teleport Rune is the skill's reagent. See `SkillTeleports`.
 */
@Component
class Teleport : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val sick = world.get(casterId, StatusEffects::class)?.hasEffect(StatusEffectId.TELEPORT_SICKNESS.id) == true
    return if (sick) OpError.TELEPORT_NOT_READY else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.teleportCaster(toHome = ctx.skillLevel >= HOME_LEVEL)

    return null
  }

  private companion object {
    const val HOME_LEVEL = 2
  }
}
