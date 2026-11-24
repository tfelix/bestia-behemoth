package net.bestia.zone.item.essence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import net.bestia.bnet.proto.ItemEssenceProto
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** A Bestia bound into a gem by Runic Etching: [bestiaId] is its species, which sets the effect. */
@Embeddable
data class Essence(
  @Column(name = "essence_bestia_id")
  val bestiaId: Long,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "essence_tier")
  val tier: GemTier,
) {
  fun toProto(): ItemEssenceProto.ItemEssence {
    return ItemEssenceProto.ItemEssence.newBuilder()
      .setBestiaId(bestiaId)
      .setTier(tier.name)
      .build()
  }
}
