package net.bestia.zone.entity

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PersistedEntityRepository : JpaRepository<PersistedEntity, Long> {

  /** All persisted entities of a kind, eagerly fetching their component blobs for reload. */
  @EntityGraph(attributePaths = ["components"])
  fun findAllByKind(kind: String): List<PersistedEntity>

  /** Existing rows for the given live entity ids, used to upsert during a sync cycle. */
  @EntityGraph(attributePaths = ["components"])
  fun findAllByEntityIdIn(entityIds: Collection<Long>): List<PersistedEntity>

  @Modifying
  @Transactional
  @Query("DELETE FROM PersistedEntity e WHERE e.entityId IN :entityIds")
  fun deleteByEntityIdIn(@Param("entityIds") entityIds: Collection<Long>)
}

/**
 * Every persisted entity of a kind, e.g. clearing stale ward entities when the world is recreated.
 *
 * Deliberately not a `@Query("DELETE FROM ...")` bulk statement: that bypasses the persistence
 * context entirely, so it never cascades to the child [PersistedComponent] rows and fails the
 * `fk_component_entity` foreign key the moment any matching entity actually has component blobs.
 * Going through [JpaRepository.deleteAll] instead loads the entities (with [findAllByKind]'s
 * `components` graph) and deletes them the normal JPA way, which does cascade.
 */
fun PersistedEntityRepository.deleteAllByKind(kind: String) {
  deleteAll(findAllByKind(kind))
}
