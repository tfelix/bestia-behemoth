package net.bestia.zone.entity

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PersistedEntityRepository : JpaRepository<PersistedEntity, Long> {

  /** All persisted entities of a kind, eagerly fetching their component blobs for reload. */
  @EntityGraph(attributePaths = ["components"])
  fun findAllByKind(kind: String): List<PersistedEntity>

  /** Existing rows for the given live entity ids, used to upsert during a sync cycle. */
  @EntityGraph(attributePaths = ["components"])
  fun findAllByEntityIdIn(entityIds: Collection<Long>): List<PersistedEntity>
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

/**
 * Every persisted entity with one of the given ids, e.g. pruning the rows of entities that have been
 * permanently removed from the world.
 *
 * This was a `@Modifying @Query("DELETE FROM PersistedEntity e WHERE e.entityId IN :entityIds")`, and it
 * had exactly the bug [deleteAllByKind] above is written to avoid - it just took longer to notice, because
 * this is the path a *mob death* takes rather than a world recreation. Every mob row has a component blob,
 * so the bulk statement failed `fk_component_entity` the first time anything died. That throw came out of
 * `EntityPersistenceService.pruneRemovedEntities`, which runs *first* in a sync cycle and whose caller
 * swallows the exception - so from the first death onwards, every persistence sync aborted before writing
 * anything at all, and the drained ids were lost with it.
 */
fun PersistedEntityRepository.deleteAllByEntityIdIn(entityIds: Collection<Long>) {
  if (entityIds.isEmpty()) {
    return
  }

  deleteAll(findAllByEntityIdIn(entityIds))
}
