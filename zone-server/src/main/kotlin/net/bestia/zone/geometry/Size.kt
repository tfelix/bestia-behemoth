package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable

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
) : Serializable {
  init {
    require(!(width < 0 || height < 0 || depth < 0)) { "Width, Height and Depth must be 0 or bigger." }
  }
}
