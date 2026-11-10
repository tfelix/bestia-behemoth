package net.bestia.zone.item.profession

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfessionResolverTest {

  @Test
  fun `the two sub-trees with the most points are the professions`() {
    val points = mapOf("SAGE" to 8, "KNIGHT" to 8, "FORESTER" to 2)

    assertEquals(
      setOf(Profession.SAGE, Profession.KNIGHT),
      ProfessionResolver.resolve(points, held = emptyList()).toSet()
    )
  }

  @Test
  fun `one point is enough to hold a profession`() {
    assertEquals(listOf(Profession.KNIGHT), ProfessionResolver.resolve(mapOf("KNIGHT" to 1), emptyList()))
  }

  @Test
  fun `on a tie the master keeps the profession they hold`() {
    val held = listOf(Profession.SAGE, Profession.FORESTER)
    val points = mapOf("SAGE" to 8, "FORESTER" to 4, "KNIGHT" to 4)

    assertEquals(listOf(Profession.SAGE, Profession.FORESTER), ProfessionResolver.resolve(points, held))
  }

  @Test
  fun `a third sub-tree with more points replaces the weaker one`() {
    val held = listOf(Profession.SAGE, Profession.FORESTER)
    val points = mapOf("SAGE" to 8, "FORESTER" to 4, "KNIGHT" to 5)

    assertEquals(listOf(Profession.SAGE, Profession.KNIGHT), ProfessionResolver.resolve(points, held))
  }

  @Test
  fun `a profession whose points are gone is dropped`() {
    assertEquals(emptyList<Profession>(), ProfessionResolver.resolve(emptyMap(), listOf(Profession.BARD)))
  }
}
