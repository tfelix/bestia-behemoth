package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity

private val LOG = KotlinLogging.logger { }

abstract class AbstractFactory<T : Blueprint>(
    val supportsType: Class<T>
) {

  fun build(entity: Entity, blueprint: Blueprint) {
    @Suppress("UNCHECKED_CAST")
    val castedBlueprint = blueprint as T
    LOG.debug { "Building Entity from: $blueprint (${supportsType.simpleName})." }
    performBuild(entity, castedBlueprint)
  }

  protected abstract fun performBuild(entity: Entity, blueprint: T)
}