package net.bestia.zone.bestia

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BestiaCatalogueTest {

  @MockK
  private lateinit var bestiaRepository: BestiaRepository

  private lateinit var sut: BestiaCatalogue

  @BeforeEach
  fun setUp() {
    // Deliberately not in id order: the catalogue's promise is that it sorts, not that the row order happens
    // to be right.
    every { bestiaRepository.findAll() } returns listOf(bestia(2, "goblin"), bestia(1, "blob"))
    sut = BestiaCatalogue(bestiaRepository)
  }

  @Test
  fun `all returns every species ordered by id`() {
    assertEquals(listOf(1L, 2L), sut.all().map { it.id })
  }

  @Test
  fun `byId returns the matching species`() {
    assertEquals("blob", sut.byId(1).identifier)
  }

  @Test
  fun `byIdentifier returns the matching species`() {
    assertEquals(2L, sut.byIdentifier("goblin").id)
  }

  @Test
  fun `byId with an unknown id throws BestiaNotFoundException`() {
    assertThrows(BestiaNotFoundException::class.java) { sut.byId(99) }
  }

  @Test
  fun `byIdentifier with an unknown identifier throws BestiaNotFoundException`() {
    assertThrows(BestiaNotFoundException::class.java) { sut.byIdentifier("wyvern") }
  }

  /** The whole reason the class exists: a spawn must not reach the database. */
  @Test
  fun `repeated lookups read the repository once`() {
    repeat(50) {
      sut.byId(1)
      sut.byIdentifier("goblin")
      sut.all()
    }

    verify(exactly = 1) { bestiaRepository.findAll() }
  }

  private fun bestia(id: Long, identifier: String): Bestia {
    return Bestia(
      id = id,
      identifier = identifier,
      level = 3,
      experienceReward = 5,
      health = 10,
      mana = 8
    )
  }
}
