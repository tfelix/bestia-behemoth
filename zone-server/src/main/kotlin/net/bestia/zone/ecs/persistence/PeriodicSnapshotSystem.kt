package net.bestia.zone.ecs.persistence

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Fixed
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import io.github.oshai.kotlinlogging.KotlinLogging

class PeriodicSnapshotSystem : IteratingSystem(
  family { all(Persistent) },
  interval = Fixed(30f)
) {
  override fun onTickEntity(entity: Entity) {

  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
