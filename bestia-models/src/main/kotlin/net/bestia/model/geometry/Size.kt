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
    val height: Long = 0
) : Serializable {
  init {
    if (width < 0 || height < 0) {
      throw IllegalArgumentException("Width and Height must be 0 or bigger.")
    }
  }
}
