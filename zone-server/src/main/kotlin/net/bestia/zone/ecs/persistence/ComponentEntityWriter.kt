package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import kotlin.reflect.KClass

abstract class ComponentEntityWriter<C : Component, T>(
  private val componentType: KClass<C>,
) {
  fun persistChanges(world: World, entityId: EntityId, entity: T) {
    val component = world.get(entityId, componentType) ?: return
    updateEntity(component, entity)
  }

  abstract fun updateEntity(comp: C, entity: T)
}