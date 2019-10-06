package net.bestia.model.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EntityPositionRepository : JpaRepository<EntityPosition, Long> {
  @Query("SELECT ep FROM EntityPosition ep")
  fun findAllInside(x1: Long, y1: Long, z1: Long, x2: Long, y2: Long, z2: Long): Iterable<EntityPosition>
}