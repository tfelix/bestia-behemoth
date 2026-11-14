package net.bestia.worldgen.history

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CultFate
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.RelationKind
import net.bestia.worldgen.core.ThreadKind
import net.bestia.worldgen.core.ThreadRecord

/**
 * Finds the stories a history left open, one [ThreadRecord] each.
 *
 * Reads a chronicle that still holds every event: an end that pruning dropped would make a settled story look open.
 */
internal class QuestThreads(private val chronicle: Chronicle) {

  private val byId = chronicle.events.associateBy { it.id }

  fun all(): List<ThreadRecord> {
    return lostArtifacts() + artifactsAway() + openOaths() + bosses(MonsterFate.SEALED, ThreadKind.BOSS_SEALED) +
        bosses(MonsterFate.WITHDRAWN, ThreadKind.BOSS_AT_LARGE) + bosses(MonsterFate.VANISHED, ThreadKind.BOSS_AT_LARGE) +
        housesWithoutHeir() + persecutedCults() + forgottenGods()
  }

  private fun lostArtifacts(): List<ThreadRecord> {
    return chronicle.artifacts
      .filter { relic -> relic.unclaimed && chronicle.provenanceOf(relic.index).none { it.kind in LAID_TO_REST } }
      .map { relic ->
        val site = listOfNotNull(relic.restingSite.takeIf { it >= 0 }?.let { Actor(ActorType.SITE, it) })
        ThreadRecord(ThreadKind.ARTIFACT_LOST, listOf(Actor(ActorType.ARTIFACT, relic.index)) + site, relic.provenance.last())
      }
  }

  private fun artifactsAway(): List<ThreadRecord> {
    return chronicle.artifacts
      .filter { it.homeTemple >= 0 && it.keptAt != it.homeTemple && !chronicle.settlements[it.homeTemple].isRuin }
      .map { relic ->
        val actors = listOf(Actor(ActorType.ARTIFACT, relic.index), Actor(ActorType.SETTLEMENT, relic.homeTemple))
        ThreadRecord(ThreadKind.ARTIFACT_AWAY, actors, relic.provenance.last())
      }
  }

  private fun openOaths(): List<ThreadRecord> {
    val settled = chronicle.events.filter { it.kind == EventKind.VENGEANCE_FULFILLED }.flatMap { it.causes }.toSet()

    return chronicle.relations
      .filter { it.kind == RelationKind.AVENGER && it.since !in settled }
      .map { oath ->
        val against = byId.getValue(oath.since).actors.filter { it.type == ActorType.CIV }
        val actors = listOf(Actor(ActorType.FIGURE, oath.from), Actor(ActorType.FIGURE, oath.to)) + against
        ThreadRecord(ThreadKind.VENGEANCE_UNFULFILLED, actors, oath.since)
      }
  }

  private fun bosses(fate: MonsterFate, kind: ThreadKind): List<ThreadRecord> {
    return chronicle.monsters.filter { it.fate == fate }.mapNotNull { monster ->
      val self = Actor(ActorType.MONSTER, monster.index)
      val ending = lastEvent(self, FATE_EVENTS.getValue(fate)) ?: return@mapNotNull null
      ThreadRecord(kind, listOf(self, Actor(ActorType.SITE, monster.site)), ending.id)
    }
  }

  private fun housesWithoutHeir(): List<ThreadRecord> {
    val livingHouses = chronicle.figures.filter { it.deathYear == 0 }.map { it.dynasty }.toSet()

    return chronicle.dynasties.filter { it.endedYear != 0 && it.index !in livingHouses }.mapNotNull { house ->
      val crisis = chronicle.events.lastOrNull { event ->
        event.kind == EventKind.SUCCESSION_CRISIS && event.year == house.endedYear &&
            event.actors.any { it.type == ActorType.FIGURE && chronicle.figures[it.index].dynasty == house.index }
      } ?: return@mapNotNull null
      ThreadRecord(ThreadKind.HOUSE_WITHOUT_HEIR, crisis.actors, crisis.id)
    }
  }

  private fun persecutedCults(): List<ThreadRecord> {
    return chronicle.cults.filter { it.fate == CultFate.ACTIVE }.mapNotNull { cult ->
      val persecution = lastEvent(Actor(ActorType.CULT, cult.index), EventKind.CULT_PERSECUTED) ?: return@mapNotNull null
      ThreadRecord(ThreadKind.CULT_PERSECUTED, persecution.actors, persecution.id)
    }
  }

  /** One per god nobody worships any more, pointing at the shrine that fell last. */
  private fun forgottenGods(): List<ThreadRecord> {
    val worshipped = chronicle.settlements.filter { it.wasFounded && !it.isRuin }.map { it.patron }.toSet() +
        chronicle.civs.filter { it.exists }.map { it.patron } +
        chronicle.cults.filter { it.fate == CultFate.ACTIVE }.map { it.deity }

    return chronicle.deities.filter { it.index !in worshipped }.mapNotNull { deity ->
      ruinedShrinesOf(deity.index).maxByOrNull { it.since }
    }
  }

  /** The towns it was patron of that lie in ruins, and the sanctuaries of its cults that were stamped out. */
  private fun ruinedShrinesOf(deity: Int): List<ThreadRecord> {
    val god = Actor(ActorType.DEITY, deity)

    val towns = chronicle.settlements.filter { it.isRuin && it.patron == deity }.mapNotNull { town ->
      val ruin = lastEvent(Actor(ActorType.SETTLEMENT, town.index), town.ruinCause ?: return@mapNotNull null)
        ?: return@mapNotNull null
      ThreadRecord(ThreadKind.FORGOTTEN_GOD, listOf(god, Actor(ActorType.SETTLEMENT, town.index)), ruin.id)
    }

    val sanctuaries = chronicle.cults
      .filter { it.kind == CultKind.DEITY && it.deity == deity && it.fate == CultFate.WIPED_OUT && it.sanctuary >= 0 }
      .mapNotNull { cult ->
        val end = lastEvent(Actor(ActorType.CULT, cult.index), EventKind.CULT_WIPED_OUT) ?: return@mapNotNull null
        ThreadRecord(ThreadKind.FORGOTTEN_GOD, listOf(god, Actor(ActorType.SITE, cult.sanctuary)), end.id)
      }

    return towns + sanctuaries
  }

  private fun lastEvent(actor: Actor, kind: EventKind): HistoryEvent? {
    return chronicle.events.lastOrNull { it.kind == kind && actor in it.actors }
  }

  private companion object {
    /** An artifact put here on purpose is not lost: its tomb or monument is the story's end. */
    val LAID_TO_REST = setOf(EventKind.ARTIFACT_ENTOMBED, EventKind.ARTIFACT_SEALED)

    val FATE_EVENTS = mapOf(
      MonsterFate.SEALED to EventKind.MONSTER_SEALED,
      MonsterFate.WITHDRAWN to EventKind.MONSTER_WITHDREW,
      MonsterFate.VANISHED to EventKind.MONSTER_VANISHED,
    )
  }
}
