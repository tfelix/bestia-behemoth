package net.bestia.zone.account.master

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import net.bestia.zone.util.EntityId
import org.springframework.transaction.annotation.Transactional

abstract class EntityPersistenceService<T>(
  private val writers: List<ComponentEntityWriter<*, T>>,
) {

  /**
   * Takes [world] as a parameter rather than injecting it: callers driven from inside an ECS
   * [net.bestia.zone.ecs.core.System] (collected into the `ecsWorld` bean's `List<System>`)
   * already hold a [WorldView] on the call stack, and injecting one here instead would make this
   * service depend on the `ecsWorld` bean while also being one of its indirect dependents - an
   * unresolvable circular reference.
   *
   * Must be open so springs proxy can override it in the child classes and properly open
   * a transaction.
   */
  @Transactional
  open fun persistEntity(world: WorldView, entityId: EntityId, dbEntityId: Long) {
    val dbEntity = loadEntity(dbEntityId)
    world.read {
      writers.forEach { writer ->
        writer.persistChanges(this, entityId, dbEntity)
      }
    }
    saveEntity(dbEntity)
  }

  abstract fun loadEntity(entityId: Long): T
  abstract fun saveEntity(entity: T)
}