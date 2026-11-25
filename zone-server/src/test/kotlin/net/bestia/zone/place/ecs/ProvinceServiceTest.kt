package net.bestia.zone.place.ecs

import net.bestia.zone.world.settlement.StartTownMarkets
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class ProvinceServiceTest {

  @Autowired
  private lateinit var provinces: ProvinceService

  @Autowired
  private lateinit var startTowns: StartTownMarkets

  @Test
  fun `the start towns stand in sanctuaries`() {
    val halls = startTowns.halls()

    assertTrue(halls.isNotEmpty())
    halls.forEach { hall ->
      assertTrue(provinces.isSanctuaryAt(hall.building.centre.x, hall.building.centre.y)) {
        "${hall.townName} stands in ${provinces.provinceAt(hall.building.centre.x, hall.building.centre.y)?.name}"
      }
    }
  }

  @Test
  fun `the rest of the world is no sanctuary`() {
    val all = provinces.provinces.provinces

    assertTrue(all.count { !provinces.isSanctuary(it) } > 0) { "every one of ${all.size} provinces is a sanctuary" }
  }
}
