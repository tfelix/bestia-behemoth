package net.bestia.zone.travel.knowledge

import net.bestia.zone.travel.TravelConfig
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteKnowledgeTest {

  private var now = Instant.parse("2026-10-23T12:00:00Z")
  private val clock = object : Clock() {
    override fun instant() = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
  }

  private val knowledge = RouteKnowledge(TravelConfig(knowledgeTtl = Duration.ofDays(21)), clock)
  private val master = Knower.Player(1L)
  private val stranger = Knower.Player(2L)

  @Test
  fun `what nobody saw is believed open`() {
    assertFalse(knowledge.believesBlocked(master, 7))
  }

  @Test
  fun `a blockage is believed by the one who saw it, not by others`() {
    assertTrue(knowledge.observe(master, 7, blocked = true))

    assertTrue(knowledge.believesBlocked(master, 7))
    assertFalse(knowledge.believesBlocked(stranger, 7))
  }

  @Test
  fun `seeing it open again is forgotten, and only a change bumps the revision`() {
    knowledge.observe(master, 7, blocked = true)
    val revision = knowledge.revisionOf(master)

    assertFalse(knowledge.observe(master, 7, blocked = true), "seen again, nothing new")
    assertEquals(revision, knowledge.revisionOf(master))

    assertTrue(knowledge.observe(master, 7, blocked = false))
    assertFalse(knowledge.believesBlocked(master, 7))
    assertEquals(revision + 1, knowledge.revisionOf(master))
  }

  @Test
  fun `a blockage lapses after the configured weeks`() {
    knowledge.observe(master, 7, blocked = true)

    now = now.plus(Duration.ofDays(20))
    assertTrue(knowledge.believesBlocked(master, 7))

    now = now.plus(Duration.ofDays(2))
    assertFalse(knowledge.believesBlocked(master, 7))
  }

  @Test
  fun `every belief that is learned or cleared is reported for storage`() {
    val reported = mutableListOf<Pair<Int, Instant?>>()
    knowledge.onLearned { _, edge, since -> reported.add(edge to since) }

    knowledge.observe(master, 7, blocked = true)
    knowledge.observe(master, 7, blocked = false)
    knowledge.observe(master, 8, blocked = false)

    val lapsesAt = now.plus(Duration.ofDays(21))
    assertEquals(listOf(7 to lapsesAt, 7 to null), reported, "an open edge nobody doubted is not stored")
  }

  @Test
  fun `a belief can be held for less than the usual weeks`() {
    knowledge.observe(master, 7, blocked = true, lasting = Duration.ofDays(1))

    now = now.plus(Duration.ofHours(25))

    assertFalse(knowledge.believesBlocked(master, 7))
  }
}
