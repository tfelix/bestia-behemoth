package net.bestia.zone.item.ecs

import net.bestia.bnet.proto.ItemEnchantmentsComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG
import java.time.Duration
import java.time.Instant

data class ItemEnchantmentsComponentSMSG(
  override val entityId: Long,
  val enchantments: List<Entry>,
  val removed: Boolean,
) : EntitySMSG {

  data class Entry(val enchantmentId: Long, val remainingSeconds: Float, val totalSeconds: Float)

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = ItemEnchantmentsComponentSMSGProto.ItemEnchantmentsComponentSMSG.newBuilder()
      .addAllEnchantments(enchantments.map { entry ->
        ItemEnchantmentsComponentSMSGProto.GroundItemEnchantment.newBuilder()
          .setEnchantmentId(entry.enchantmentId.toInt())
          .setRemainingSeconds(entry.remainingSeconds)
          .setTotalSeconds(entry.totalSeconds)
          .build()
      })
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setItemEnchantments(component))
  }

  companion object {
    fun of(entityId: Long, ground: ItemEnchantments, now: Instant, removed: Boolean): ItemEnchantmentsComponentSMSG {
      val entries = ground.enchantments.map { enchantment ->
        val endsAt = enchantment.endsAt
        Entry(
          enchantmentId = enchantment.enchantment.id,
          remainingSeconds = endsAt?.let { secondsBetween(now, it) } ?: 0f,
          totalSeconds = endsAt?.let { secondsBetween(ground.since, it) } ?: 0f,
        )
      }

      return ItemEnchantmentsComponentSMSG(entityId, entries, removed)
    }

    private fun secondsBetween(from: Instant, to: Instant): Float {
      return (Duration.between(from, to).toMillis().coerceAtLeast(0) / 1000.0).toFloat()
    }
  }
}
