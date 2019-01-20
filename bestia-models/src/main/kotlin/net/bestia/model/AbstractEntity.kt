package net.bestia.model

import java.util.*
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

abstract class AbstractEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  val id: Long = 0

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