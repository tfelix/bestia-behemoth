package net.bestia.zone.etching

import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** Rolls every etching whose time is up, each on the job key of the Bestia's owner, like every storage change. */
@Component
class EtchingJobResolver(
  private val etchings: EtchingJobs,
  private val asyncJobs: AsyncJobExecutor,
) {

  /** Due etchings handed on and not yet rolled, so a slow queue does not get the same one twice. */
  private val queued = ConcurrentHashMap.newKeySet<PlayerBestiaId>()

  @Scheduled(fixedDelay = 5_000L)
  fun resolveDue() {
    for ((playerBestiaId, ownerMasterId) in etchings.due()) {
      if (!queued.add(playerBestiaId)) continue

      asyncJobs.submit(ownerMasterId) {
        try {
          etchings.resolve(playerBestiaId)
        } finally {
          queued.remove(playerBestiaId)
        }
      }
    }
  }
}
