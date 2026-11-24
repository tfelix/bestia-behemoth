package net.bestia.zone.bestiastorage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Bestia Ledger opens the storage anywhere. The client sends `OpenBestiaStorageCMSG` for it, so using it
 * here always refuses and keeps the ledger; it only exists because every usable item needs a script.
 */
@Component
class BestiaLedgerScript : ItemScript {

  override val itemId: Long = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    LOG.debug { "Entity $userId used a Bestia Ledger directly; the storage opens through OpenBestiaStorageCMSG" }
    return false
  }

  /** Whether the master holds a ledger, which opens the storage anywhere. Called inside a world scope. */
  fun grants(world: World, masterEntityId: EntityId): Boolean {
    return world.get(masterEntityId, Inventory::class)?.hasItem(ITEM_ID.toInt()) == true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** `bestia_ledger` in items.yml. */
    const val ITEM_ID = 627L
  }
}
