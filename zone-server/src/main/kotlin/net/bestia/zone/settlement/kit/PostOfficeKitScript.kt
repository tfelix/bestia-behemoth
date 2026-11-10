package net.bestia.zone.settlement.kit

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

/** The Post Office Kit (`items.yml` id 150), made with Carpentry. Only a standing town without one takes it. */
@Component
class PostOfficeKitScript(
  structures: PlayerStructureService,
  private val towns: TownService,
  private val outMessageProcessor: OutMessageProcessor,
) : StructureKitScript(structures, StaticEntityKind.POST_OFFICE, BUILD_SECONDS) {

  override val itemId = 150L

  override fun mayBuildAt(world: World, userId: EntityId, at: Vec3L): Boolean {
    val refusal = towns.buildingRefusal(at, StaticEntityKind.POST_OFFICE) ?: return true
    world.get(userId, Account::class)?.accountId?.let { outMessageProcessor.sendToPlayer(it, OperationErrorSMSG(refusal)) }

    return false
  }

  private companion object {
    /** 30 minutes of work: a building a group puts up together. */
    const val BUILD_SECONDS = 1800f
  }
}
