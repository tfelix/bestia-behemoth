package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That a townsperson's name is a function of who they are and nothing else.
 *
 * The property worth pinning is not which name comes out - that is a word pool's business - but that the
 * same three indices always produce the same one. Townsfolk are destroyed when the last player walks away
 * and rebuilt from their identity when somebody returns, so a name drawn from anything else would change
 * while the player's back was turned.
 */
class TownsfolkNamingTest {

  private val worldService = mockk<WorldService>()
  private val sut = TownsfolkNaming(worldService)

  init {
    val record = mockk<PersistedWorld>(relaxed = true)
    every { record.seed } returns 0xBE571AL
    every { worldService.record } returns record

    // An empty chronicle names nobody, so every culture lookup falls to the default pool. Which pool is
    // not what is under test; that one is always chosen the same way is.
    val generated = mockk<GeneratedWorld>(relaxed = true)
    every { worldService.generated } returns generated
    every { generated.world.chronicle } returns mockk<Chronicle>(relaxed = true)
  }

  @Test
  fun `the same person is named the same twice`() {
    val identity = TownsfolkIdentity.of(settlement = 12, household = 3, member = 1)

    assertEquals(sut.nameOf(identity), sut.nameOf(identity))
    assertEquals(sut.seedOf(identity), sut.seedOf(identity))
  }

  @Test
  fun `two people in one household are two people`() {
    val head = TownsfolkIdentity.of(settlement = 12, household = 3, member = 0)
    val child = TownsfolkIdentity.of(settlement = 12, household = 3, member = 1)

    // On the seed rather than the name: a pool of fourteen given names per culture is meant to collide,
    // and two neighbours sharing one is how a village sounds. Being the same *person* is the bug.
    assertNotEquals(sut.seedOf(head), sut.seedOf(child))
  }

  @Test
  fun `the same member index in two households is two people`() {
    assertNotEquals(
      sut.seedOf(TownsfolkIdentity.of(settlement = 12, household = 3, member = 0)),
      sut.seedOf(TownsfolkIdentity.of(settlement = 12, household = 4, member = 0)),
    )
  }

  @Test
  fun `a name is a name`() {
    val name = sut.nameOf(TownsfolkIdentity.of(settlement = 12, household = 3, member = 0))

    assertTrue(name.isNotBlank(), "a nameplate would read empty")
    assertEquals(name.replaceFirstChar { it.uppercase() }, name, "'$name' is not capitalised")
  }
}
