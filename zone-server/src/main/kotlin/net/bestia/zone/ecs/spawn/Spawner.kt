package net.bestia.zone.ecs.spawn

import net.bestia.zone.ecs2.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

class Spawner(
  val bestiaId: Long,
  val maxSpawnCount: Int = 1,
  val position: Vec3L,
  val range: Int,
) : Component {

  init {
    require(range >= 1) { "Range must be >= 1" }
  }

  var spawnedEntities: MutableSet<EntityId> = mutableSetOf()
}

