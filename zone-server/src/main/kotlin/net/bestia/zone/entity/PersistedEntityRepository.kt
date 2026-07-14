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
