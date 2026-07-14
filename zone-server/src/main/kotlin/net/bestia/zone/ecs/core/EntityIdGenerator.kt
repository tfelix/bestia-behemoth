package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

interface EntityIdGenerator {
  fun nextId(): EntityId
}