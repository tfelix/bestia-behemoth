package net.bestia.model.entity

import net.bestia.model.AbstractEntity
import net.bestia.model.geometry.Vec3
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "entity_positions")
class EntityPosition(
    @Id
    val entityId: Long,
    val x: Long,
    val y: Long,
    val z: Long
) {
  fun toPoint(): Vec3 {
    return Vec3(x, y, z)
  }

  override fun hashCode(): Int {
    return Objects.hashCode(entityId)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other)
      return true
    if (other == null)
      return false
    if (javaClass != other.javaClass)
      return false
    val otherObj = other as AbstractEntity
    return entityId == otherObj.id
  }
}