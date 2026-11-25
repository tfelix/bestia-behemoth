package net.bestia.zone.prop.kit

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Observation Outpost Kit (`items.yml` id 644): put up in the wild, it becomes the lookout Observation is cast
 * at. Only a Master who knows Observation can raise one; anybody may use it once it stands.
 */
@Component
class ObservationOutpostKitScript(
  structures: PlayerStructureService,
  private val skillIds: SkillIds,
  private val outMessageProcessor: OutMessageProcessor,
) : StructureKitScript(structures, StaticEntityKind.OBSERVATION_OUTPOST, BUILD_SECONDS) {

  override val itemId = 644L

  override fun mayBuildAt(world: World, userId: EntityId, at: Vec3L): Boolean {
    val observation = skillIds[SkillId.M_OBSERVATION] ?: return false
    if ((world.get(userId, KnownSkills::class)?.levelOf(observation) ?: 0) >= 1) return true

    world.get(userId, Account::class)?.accountId?.let {
      outMessageProcessor.sendToPlayer(it, OperationErrorSMSG(OpError.OBSERVATION_OUTPOST_NEEDS_SKILL))
    }
    return false
  }

  private companion object {
    /** A platform of poles and planks: a few minutes of work. */
    const val BUILD_SECONDS = 120f
  }
}
