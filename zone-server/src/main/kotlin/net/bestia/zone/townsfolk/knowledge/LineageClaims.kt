package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.SettlementLoreService
import kotlin.math.hypot

/**
 * Families who say they come down from one of the old ruling houses.
 *
 * The hook for heirloom quests: a house's relics may still lie somewhere, and somebody in town claims
 * them by blood. Nobody can prove a claim, so nothing checks it.
 */
object LineageClaims {

  /** Topics of claims: inside the legends' band, well above any hidden place's index. */
  const val TOPIC_BASE = LegendKnowledge.TOPIC_BASE + 5_000

  const val SLOT_HOUSE = "house"

  /** One claim for each house that ruled from near [settlement] and that a family here claims. */
  fun at(
    chronicle: Chronicle,
    settlement: Int,
    worldSeed: Long,
    positions: Map<Int, Vec2d>,
    variants: Int = 1,
  ): List<Knowledge> {
    val here = positions[settlement] ?: return emptyList()

    return chronicle.dynasties
      .filter { house ->
        val seat = positions[house.seat] ?: return@filter false
        hypot(seat.x - here.x, seat.y - here.y) <= SettlementLoreService.NEARBY_RANGE &&
          GenRng.hashUnit(worldSeed, house.index.toLong(), settlement.toLong(), CLAIM_SALT) < CLAIM_CHANCE
      }
      .mapNotNull { house ->
        val name = ChronicleNames.houseOf(chronicle, house.index) ?: return@mapNotNull null

        Knowledge(
          topic = TOPIC_BASE + house.index,
          key = LegendKind.LINEAGE.key,
          slots = mapOf(
            SLOT_HOUSE to Knowledge.Slot.Name(name),
            HistoryKnowledge.SLOT_FIGURE to
              named(ChronicleNames.figureOf(chronicle, house.founder), HistoryKnowledge.UNKNOWN_FIGURE),
            HistoryKnowledge.SLOT_ERA to Knowledge.Slot.Token(Era.of(chronicle.presentYear - house.foundedYear).key),
          ),
          importance = IMPORTANCE,
          year = house.foundedYear,
          locality = Locality.OWN_TOWN,
          variants = variants,
          certainty = Certainty.TOLD,
        )
      }
  }

  private fun named(name: String?, unknownKey: String): Knowledge.Slot {
    return if (name == null) Knowledge.Slot.Token(unknownKey) else Knowledge.Slot.Name(name)
  }

  /** Half the houses that ruled from near a town have a family there that claims them. */
  private const val CLAIM_CHANCE = 0.5

  /** A family's pride, ranked like a legend: they bring it up before most news. */
  private const val IMPORTANCE = 65

  private const val CLAIM_SALT = 0x4C49L
}
