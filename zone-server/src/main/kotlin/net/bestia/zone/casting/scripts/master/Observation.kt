package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.casting.sense.EventObservation
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Observation (`skills.yml` id 48): cast at an Observation Outpost. See [EventObservation]. */
@Component
class Observation(
  private val observation: EventObservation,
) : SkillStrategy {

  /** Read only, before the cast bar: without an outpost there is nothing to cast at. */
  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? =
    if (observation.outpostNear(world, casterId) == null) OpError.OBSERVATION_NO_OUTPOST else null

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.observeEvents(ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
  }
}
