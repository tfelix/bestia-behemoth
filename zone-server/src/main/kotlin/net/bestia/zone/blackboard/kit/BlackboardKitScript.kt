package net.bestia.zone.blackboard.kit

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.kit.StructureKitScript
import net.bestia.zone.settlement.TownService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The Blackboard Kit (`items.yml` id 613), made with Carpentry. Only a standing town without a board takes it. */
@Component
class BlackboardKitScript(
  structures: PlayerStructureService,
  private val towns: TownService,
  private val outMessageProcessor: OutMessageProcessor,
) : StructureKitScript(structures, StaticEntityKind.BLACKBOARD, BUILD_SECONDS) {

  override val itemId = 613L

  override fun mayBuildAt(world: World, userId: EntityId, at: Vec3L): Boolean {
    val refusal = towns.buildingRefusal(at, StaticEntityKind.BLACKBOARD) ?: return true
    world.get(userId, Account::class)?.accountId?.let { outMessageProcessor.sendToPlayer(it, OperationErrorSMSG(refusal)) }

    return false
  }

  private companion object {
    /** 5 minutes of work: a board and two posts. */
    const val BUILD_SECONDS = 300f
  }
}
