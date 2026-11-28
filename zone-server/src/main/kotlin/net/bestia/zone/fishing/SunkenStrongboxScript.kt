package net.bestia.zone.fishing

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Prising a strongbox open: the gear sealed in it when it was fished up goes into the bag that held it. */
@Component
class SunkenStrongboxScript(
  private val bundles: ItemBundles,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
) : ItemScript {

  override val itemId: Long = SunkenStrongbox.ITEM_ID

  /** A strongbox is always one instance, so it is only ever opened through [executeHeld]. */
  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    return false
  }

  override fun executeHeld(world: World, userId: EntityId, args: ScriptArgs, uniqueId: Long): Boolean {
    if (uniqueId == 0L) {
      return false
    }
    val owner = ItemOwner.of(world, userId) ?: return false

    asyncJobExecutor.submit(key = owner.masterId) {
      val arrived = bundles.unpack(uniqueId, owner.masterId, owner.playerBestiaId)
      worldView.grantToLive(userId, arrived)
    }

    return true
  }
}
