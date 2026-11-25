package net.bestia.zone.etching

import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureDestroyedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** A Carving Table knocked down or left to decay gives every Bestia locked into it back to its master's storage. */
@Component
class CarvingTableRemoval(
  private val storage: BestiaStorageService,
  private val jobs: AsyncJobExecutor,
) {

  @EventListener
  fun onDestroyed(event: PlayerStructureDestroyedEvent) {
    if (event.kind != StaticEntityKind.CARVING_TABLE) return

    jobs.submit(event.structureId) { storage.returnAllFrom(event.structureId) }
  }
}
