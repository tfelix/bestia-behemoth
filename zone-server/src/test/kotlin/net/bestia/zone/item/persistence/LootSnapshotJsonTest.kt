package net.bestia.zone.item.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.stat.ItemRoll
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest

/** A ground item survives a restart as JSON, so its maker and roll have to as well. */
@JsonTest
class LootSnapshotJsonTest {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `a crafted ground item keeps its maker and roll through a save`() {
    val snapshot = LootSnapshot(
      entityId = 1L, itemId = 2L, amount = 1, uniqueId = 0L, x = 3, y = 4, z = 5,
      provenance = ItemProvenance(crafterName = "Alice", worldName = "Genesis"),
      roll = ItemRoll(stats = listOf(StatValue(ItemStat.STR, 3)), sockets = 1, itemLevel = 30),
    )

    assertEquals(snapshot, objectMapper.readValue<LootSnapshot>(objectMapper.writeValueAsString(snapshot)))
  }

  @Test
  fun `a row written before makers existed reads as a plain item`() {
    val json = """{"entityId":1,"itemId":2,"amount":1,"uniqueId":0,"x":3,"y":4,"z":5}"""

    val loaded = objectMapper.readValue<LootSnapshot>(json)

    assertEquals(null, loaded.provenance)
    assertEquals(null, loaded.roll)
  }
}
