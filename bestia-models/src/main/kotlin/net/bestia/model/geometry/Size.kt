package net.bestia.model.geometry

import jakarta.persistence.Embeddable

/**
 * Immutable size object.
 *
 * @author Thomas Felix
 */
@Embeddable
data class Size(
    val width: Long = 0,
    val height: Long = 0,
    val depth: Long = 0
) {
  init {
    require(!(width < 0 || height < 0 || depth < 0)) { "Width, Height and Depth must be 0 or bigger." }
  }
}
