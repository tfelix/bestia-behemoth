package net.bestia.model.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EntityPositionRepository : JpaRepository<Long, EntityPosition> {
  fun findAllInside(x1: Long, y1: Long, z1: Long, x2: Long, y2: Long, z2: Long): Iterable<EntityPosition>
}