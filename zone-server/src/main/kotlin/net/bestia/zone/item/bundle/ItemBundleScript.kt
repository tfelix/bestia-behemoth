package net.bestia.zone.item.bundle

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Unpack: everything in the bundle goes into the bag that held it. That bag can always carry it, since the bundle
 * weighed what it held.
 */
@Component
class ItemBundleScript(
  private val bundles: ItemBundles,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
) : ItemScript {

  override val itemId: Long = ItemBundles.ITEM_ID

  /** A bundle is always one instance, so it is only ever used through [executeHeld]. */
  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    return false
  }

  override fun executeHeld(world: World, userId: EntityId, args: ScriptArgs, uniqueId: Long): Boolean {
    val owner = ItemOwner.of(world, userId) ?: return false

    asyncJobExecutor.submit(key = owner.masterId) {
      val arrived = bundles.unpack(uniqueId, owner.masterId, owner.playerBestiaId)
      worldView.grantToLive(userId, arrived)
    }

    return true
  }
}
