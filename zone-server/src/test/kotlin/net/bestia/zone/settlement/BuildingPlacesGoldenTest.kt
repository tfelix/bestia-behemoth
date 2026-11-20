package net.bestia.zone.settlement

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import net.bestia.zone.entity.StaticEntityKind
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * The client's kit ghost applies its own copy of [BuildingPlaces]. Both sides compare against one golden file, so
 * the copies cannot drift apart unseen. Run with `UPDATE_GOLDEN=1` to rewrite it.
 */
class BuildingPlacesGoldenTest {

  @Test
  fun `the building places match the file the client checks against`() {
    val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    val written = mapper.writeValueAsString(describe()) + "\n"

    if (System.getenv("UPDATE_GOLDEN") != null) GOLDEN.writeText(written)

    assertEquals(GOLDEN.readText(), written, "run with UPDATE_GOLDEN=1, then fix the client's copy to match")
  }

  /** Kinds by name with their ordinal, which is what the client sees on the wire. */
  private fun describe(): Map<String, Any> {
    return linkedMapOf(
      "anchorRangeMetres" to BuildingPlaces.ANCHOR_RANGE_METRES,
      "wildSpacingMetres" to BuildingPlaces.WILD_SPACING_METRES,
      "citySign" to StaticEntityKind.CITY_SIGN.ordinal,
      "wildKinds" to ordinalsOf { it in BuildingPlaces.WILD_KINDS },
      "townAnchorKinds" to ordinalsOf { BuildingPlaces.anchorsTown(it) },
      "generatedBuildingKinds" to ordinalsOf { it.name.startsWith("BUILDING_") || it == StaticEntityKind.AUCTION_HOUSE },
    )
  }

  private fun ordinalsOf(filter: (StaticEntityKind) -> Boolean): Map<String, Int> {
    return StaticEntityKind.entries.filter(filter).associate { it.name to it.ordinal }.toSortedMap()
  }

  private companion object {
    val GOLDEN = File("src/test/resources/building-places.golden.json")
  }
}
