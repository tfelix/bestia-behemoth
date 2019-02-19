package net.bestia.zoneserver.battle

import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException

@Component
class StatusServiceFactory(
    private val statusServices: List<StatusService>
) {

  fun getStatusService(entity: Entity): StatusService {
    return statusServices.find { it.createsStatusFor(entity) }
        ?: throw IllegalArgumentException("No StatusService found for creating status for $entity")
  }
}