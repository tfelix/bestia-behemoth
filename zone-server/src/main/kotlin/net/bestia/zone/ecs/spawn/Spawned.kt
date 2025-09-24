package net.bestia.zone.ecs.spawn

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.boot.SpawnerManager

/**
 * This component is added to an entity which was created from a spawner. So when it is
 * removed the spawner can be notified and schedule further action.
 */
data class Spawned(
  val spawnerId: Long
) : Component<Spawned> {

  override fun World.onRemove(entity: Entity) {
    inject<SpawnerManager>().spawnedEntityRemoved(spawnerId)
  }

  override fun type() = Spawned

  companion object : ComponentType<Spawned>()
}
