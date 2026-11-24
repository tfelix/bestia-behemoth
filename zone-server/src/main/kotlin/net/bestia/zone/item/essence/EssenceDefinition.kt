package net.bestia.zone.item.essence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * What a species gives as an essence: the `essence:` line of its mob YAML. [target] is what the effect aims at,
 * a kind, an element, a status, an attack or an attribute pair, see [EssenceEffect].
 */
@Embeddable
data class EssenceDefinition(
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "essence_group")
  val group: SocketGroup,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "essence_effect")
  val effect: EssenceEffect,

  @Column(name = "essence_target")
  val target: String? = null,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "essence_drawback")
  val drawback: EssenceDrawback? = null,
) {
  fun pointsIn(tier: GemTier): Int {
    return if (drawback == null) tier.points else tier.points * 3 / 2
  }
}
