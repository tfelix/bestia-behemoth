package net.bestia.zone.prop

import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.prop.persistence.WorldObjectDivergenceRepository

/**
 * Every felled tree and claimed landmark. `WorldObjectDivergence` carries a `worldShapeVersion` now, so this
 * is the tidy half like the charts rather than the correctness half - but it was the correctness half until
 * that column existed, because `pipelineVersion` does not fold the seed and a reseeded world therefore matched
 * on the only guard there was.
 */
@Component
@Order(2)
class WorldObjectDivergenceWipe(
  private val worldObjectDivergenceRepository: WorldObjectDivergenceRepository,
) : WorldScopedData {

  override fun wipe() {
    worldObjectDivergenceRepository.deleteAll()
  }
}
