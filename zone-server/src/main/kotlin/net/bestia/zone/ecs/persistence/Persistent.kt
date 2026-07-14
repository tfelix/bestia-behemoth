package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.core.Component

/**
 * Opt-in marker: only entities carrying this tag are considered by the periodic
 * [EntityPersistenceService] and the startup loader. Long-lived world entities
 * (mobs, dropped items, player masters/bestias) get it from their factory;
 * short-lived visual entities (spell/projectile effects) deliberately never do,
 * so they are simply not persisted.
 *
 * Used as the query driver so periodic iteration only touches persistent entities.
 */
data object Persistent : Component
