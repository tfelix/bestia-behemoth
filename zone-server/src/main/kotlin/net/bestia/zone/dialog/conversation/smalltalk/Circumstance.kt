package net.bestia.zone.dialog.conversation.smalltalk

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.environment.time.Season

/**
 * Everything true of one speaker, in one place, on one day, that a small-talk line may be gated on.
 *
 * Gathered once and handed to the catalogue, rather than letting each line ask the world for itself: the
 * expensive halves here are a region lookup and a weather evaluation, and a pool of thirty lines each
 * doing its own would do both thirty times to answer one question.
 *
 * Nothing in here is about the *conversation*. These are facts about a person and a place, which is what
 * keeps the pool from being generic filler - a line is only ever offered where the thing it mentions is
 * actually the case.
 */
data class Circumstance(
  val occupation: String,
  val age: Int,
  val kinship: Kinship,
  val terrain: RegionKind,
  val season: Season,
  val weather: WeatherKind,
  /** Whether the walls ever went up, at any point in the town's history. */
  val walled: Boolean,
  /** Whether the town has ever been sacked. Not how often - once is the thing people still talk about. */
  val sacked: Boolean,
  /** 0 to 1, the settlement's own. What separates a line about good cloth from one about a bad year. */
  val wealth: Double,
)
