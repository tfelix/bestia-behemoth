package net.bestia.zone.item.stat

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import org.springframework.stereotype.Component
import java.util.Random
import kotlin.math.floor
import kotlin.math.min

/**
 * Fills an item's [RandomStatTemplate] for one new copy.
 *
 * [cap] is the highest stat level the roll may reach, from the maker's skill and material. Sockets roll first
 * and use part of it. Every stat then rolls a quality as the lower of two dice, so a top roll is rare, and the
 * whole roll is scaled down if the stats together went past the cap.
 */
@Component
class ItemStatRoller(
  private val random: Random = ThreadLocalRandomSource
) {

  fun roll(sheet: ItemStatSheet, cap: Int): ItemRoll {
    val template = sheet.randomStats ?: return ItemRoll(emptyList(), 0, sheet.itemLevel)
    val capPoints = cap.toDouble() / ItemLevel.LEVELS_PER_POINT

    val sockets = rollSockets(template.socketChances, capPoints)
    val statPoints = capPoints - sockets * ItemLevel.POINTS_PER_SOCKET
    val stats = scaledToCap(template.slots.mapNotNull { rollSlot(it, statPoints) }, statPoints)

    return ItemRoll(stats, sockets, ItemLevel.of(sheet.baseLevel, sheet.fixedStats() + stats, sockets))
  }

  /** A socket only opens when the one before it did, and when the cap still has room for it. */
  private fun rollSockets(chances: List<Int>, capPoints: Double): Int {
    var sockets = 0
    for (chance in chances) {
      val fits = (sockets + 1) * ItemLevel.POINTS_PER_SOCKET <= capPoints
      if (!fits || !rolls(chance)) break
      sockets++
    }

    return sockets
  }

  private fun rollSlot(slot: RandomStatTemplate.Slot, statPoints: Double): StatValue? {
    if (!rolls(slot.chance)) return null

    val stat = slot.candidates[random.nextInt(slot.candidates.size)]
    val quality = min(random.nextDouble(), random.nextDouble())
    val value = floor(quality * statPoints * stat.unitsPerPoint).toInt()
      .coerceAtLeast(slot.min)
      .coerceAtMost(slot.max ?: Int.MAX_VALUE)

    return StatValue(stat, value)
  }

  private fun scaledToCap(stats: List<StatValue>, statPoints: Double): List<StatValue> {
    val points = ItemLevel.pointsOf(stats)
    if (points <= statPoints) return stats

    val share = statPoints.coerceAtLeast(0.0) / points

    return stats
      .map { it.copy(value = floor(it.value * share).toInt()) }
      .filter { it.value > 0 }
  }

  private fun rolls(percent: Int): Boolean {
    return random.nextInt(100) < percent
  }
}
