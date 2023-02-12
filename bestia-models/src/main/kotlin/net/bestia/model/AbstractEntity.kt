package net.bestia.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.util.*

@MappedSuperclass
abstract class AbstractEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0

  final override fun hashCode(): Int {
    return Objects.hashCode(id)
  }

  final override fun equals(other: Any?): Boolean {
    if (this === other)
      return true
    if (other == null)
      return false
    if (javaClass != other.javaClass)
      return false
    val otherObj = other as AbstractEntity
    return id == otherObj.id
  }
}