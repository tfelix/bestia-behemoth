package net.bestia.model.geometry

import java.io.Serializable
import java.util.Objects

import javax.persistence.Embeddable

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
