package net.bestia.model.item

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

enum class CraftType {
  ALCHEMY,
  FORGERY
}

class ResourceMatrix(
    val craftType: CraftType
) {

  val slots: Array<ResourceEntry?> = arrayOfNulls(MAX_SIZE * MAX_SIZE)

  fun get(x: Int, y: Int): ResourceEntry? {
    require(x in 0 until MAX_SIZE) { "x must be between 0 and ${MAX_SIZE - 1}" }
    require(y in 0 until MAX_SIZE) { "y must be between 0 and ${MAX_SIZE - 1}" }

    return slots[y * MAX_SIZE + x]
  }

  fun set(x: Int, y: Int, resourceEntry: ResourceEntry) {
    require(x in 0 until MAX_SIZE) { "x must be between 0 and ${MAX_SIZE - 1}" }
    require(y in 0 until MAX_SIZE) { "y must be between 0 and ${MAX_SIZE - 1}" }

    slots[y * MAX_SIZE + x] = resourceEntry
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ResourceMatrix

    if (!slots.contentEquals(other.slots)) return false

    return true
  }

  override fun hashCode(): Int {
    return slots.contentHashCode()
  }

  companion object {
    const val MAX_SIZE = 5
    val MAPPER = ObjectMapper()
        .registerModule(KotlinModule())!!
  }
}