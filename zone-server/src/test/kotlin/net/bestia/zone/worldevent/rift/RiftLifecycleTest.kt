package net.bestia.zone.worldevent.rift

import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaFieldConfig
import net.bestia.zone.world.mana.ManaFieldRule
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.RiftLifecycle.News
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A whole rift on a real field, with the field's own creep and leak running beside it, to check it feels as the
 * world events design asks: an eerie calm around a knot, five stages, and a collapse that leaves at least storms
 * within 300 m.
 */
class RiftLifecycleTest {

  private val field = ManaField()
  private val ground = object : ManaFieldRule.Ground {
    override fun startDensity(cell: Cell) = START
    override fun isSanctuary(cell: Cell) = false
  }
  private val config = RiftConfig()
  private val riftField = RiftField(field, ground)
  private val lifecycle = RiftLifecycle(config, riftField, realSecondsPerBestiaSecond = 1.0 / SPEED)
  private val rule = ManaFieldRule(ManaFieldConfig(), realSecondsPerBestiaDay = DAY / SPEED)

  private var now = 0L

  @Test
  fun `a knot calms its ground, opens through five stages and its collapse leaves storms`() {
    thicken(radius = 2_000.0, to = THICK)
    val rift = knot(opensAfterBestiaDays = 3.5)

    val news = runRealMinutes(rift, minutes = (3.5 * DAY / SPEED / 60).toInt() - 1)
    assertTrue(news.isEmpty(), "nothing happens while it gathers: $news")
    assertTrue(densityAt(CENTRE, CENTRE) < 0.35, "the ground around a knot is eerily calm: ${densityAt(CENTRE, CENTRE)}")

    val opened = runRealMinutes(rift, minutes = 2)
    assertEquals(listOf(News.OPENED), opened)

    val stages = HashSet<Int>()
    val collapsed = runRealMinutes(rift, minutes = 24 * 60 + 2) { stages += rift.stage }
    assertEquals(listOf(News.COLLAPSED), collapsed)
    assertEquals((1..5).toSet(), stages)

    val within = field.cellsWithin(CENTRE, CENTRE, config.spillRadiusMetres)
    assertTrue(within.all { densityOf(it) >= 0.60 }, "a collapse leaves storms or worse within 300 m")
  }

  @Test
  fun `a knot whose land is drained loses its hold and lets its mana go`() {
    thicken(radius = 2_000.0, to = THICK)
    val rift = knot(opensAfterBestiaDays = 3.0)
    runRealMinutes(rift, minutes = 60)
    val gathered = rift.reservoir
    assertTrue(gathered > 0.0)

    thicken(radius = 2_000.0, to = 0.4)
    val news = runRealMinutes(rift, minutes = 1)

    assertEquals(listOf(News.DISSOLVED), news)
    assertEquals(Phase.DISSOLVED, rift.phase)
    assertEquals(0.0, rift.reservoir)
  }

  @Test
  fun `the older of two touching rifts swallows the younger`() {
    val older = StoredRift(x = CENTRE, y = CENTRE, phase = Phase.KNOT, reservoir = 100.0).also { it.id = 1 }
    val younger = StoredRift(x = CENTRE + 400, y = CENTRE, phase = Phase.OPEN, stage = 2, reservoir = 50.0).also { it.id = 2 }

    lifecycle.merge(listOf(older, younger))

    assertEquals(150.0, older.reservoir)
    assertEquals(Phase.OPEN, older.phase)
    assertEquals(Phase.MERGED, younger.phase)
  }

  private fun knot(opensAfterBestiaDays: Double) =
    StoredRift(x = CENTRE, y = CENTRE, opensAtSecond = (opensAfterBestiaDays * DAY).toLong()).also { it.id = 1 }

  private fun runRealMinutes(rift: StoredRift, minutes: Int, each: () -> Unit = {}): List<News> {
    val news = ArrayList<News>()
    repeat(minutes) {
      now += (60 * SPEED).toLong()
      lifecycle.step(rift, now, 60.0)?.let(news::add)
      rule.step(field, ground, 60.0)
      each()
    }
    return news
  }

  private fun thicken(radius: Double, to: Double) =
    field.cellsWithin(CENTRE, CENTRE, radius).forEach { field.setDelta(it, to - START) }

  private fun densityOf(cell: Cell) = START + field.deltaOf(cell.x, cell.y)

  private fun densityAt(x: Double, y: Double) = densityOf(Cell(ManaField.cellOf(x), ManaField.cellOf(y)))

  private companion object {
    const val START = 0.13
    const val THICK = 0.72
    const val DAY = 86_400.0
    const val SPEED = 3.0
    const val CENTRE = 8_256.0
  }
}
