package net.bestia.zone.battle

import java.util.*

/**
 * Returns the attack damage modifier for a given elemental set of the attacker
 * and defender. Since this class is immutable it is thread-safe.
 *
 * @author Thomas Felix
 */
internal object ElementModifier {

  /**
   * The damage table is saved as integer. Basically its a fixed point. 100
   * means a multiplier factor of 1.00.
   */
  private val elementMap = mapOf(
          // LEVEL 1
          ElementKey(Element.NORMAL, Element.NORMAL) to 100,
          ElementKey(Element.NORMAL, Element.WATER) to 100,
          ElementKey(Element.NORMAL, Element.WATER) to 100,
          ElementKey(Element.NORMAL, Element.EARTH) to 100,
          ElementKey(Element.NORMAL, Element.FIRE) to 100,
          ElementKey(Element.NORMAL, Element.WIND) to 100,
          ElementKey(Element.NORMAL, Element.POISON) to 100,
          ElementKey(Element.NORMAL, Element.HOLY) to 100,
          ElementKey(Element.NORMAL, Element.SHADOW) to 100,
          ElementKey(Element.NORMAL, Element.GHOST) to 25,
          ElementKey(Element.NORMAL, Element.UNDEAD) to 100,

          ElementKey(Element.WATER, Element.NORMAL) to 100,
          ElementKey(Element.WATER, Element.WATER) to 25,
          ElementKey(Element.WATER, Element.EARTH) to 100,
          ElementKey(Element.WATER, Element.FIRE) to 150,
          ElementKey(Element.WATER, Element.WIND) to 50,
          ElementKey(Element.WATER, Element.POISON) to 100,
          ElementKey(Element.WATER, Element.HOLY) to 75,
          ElementKey(Element.WATER, Element.SHADOW) to 100,
          ElementKey(Element.WATER, Element.GHOST) to 100,
          ElementKey(Element.WATER, Element.UNDEAD) to 100,

          ElementKey(Element.EARTH, Element.NORMAL) to 100,
          ElementKey(Element.EARTH, Element.WATER) to 100,
          ElementKey(Element.EARTH, Element.EARTH) to 100,
          ElementKey(Element.EARTH, Element.FIRE) to 50,
          ElementKey(Element.EARTH, Element.WIND) to 150,
          ElementKey(Element.EARTH, Element.POISON) to 100,
          ElementKey(Element.EARTH, Element.HOLY) to 75,
          ElementKey(Element.EARTH, Element.SHADOW) to 100,
          ElementKey(Element.EARTH, Element.GHOST) to 100,
          ElementKey(Element.EARTH, Element.UNDEAD) to 100,

          ElementKey(Element.FIRE, Element.NORMAL) to 100,
          ElementKey(Element.FIRE, Element.WATER) to 50,
          ElementKey(Element.FIRE, Element.EARTH) to 150,
          ElementKey(Element.FIRE, Element.FIRE) to 25,
          ElementKey(Element.FIRE, Element.WIND) to 100,
          ElementKey(Element.FIRE, Element.POISON) to 100,
          ElementKey(Element.FIRE, Element.HOLY) to 75,
          ElementKey(Element.FIRE, Element.SHADOW) to 100,
          ElementKey(Element.FIRE, Element.GHOST) to 100,
          ElementKey(Element.FIRE, Element.UNDEAD) to 125,

          ElementKey(Element.WIND, Element.NORMAL) to 100,
          ElementKey(Element.WIND, Element.WATER) to 175,
          ElementKey(Element.WIND, Element.EARTH) to 50,
          ElementKey(Element.WIND, Element.FIRE) to 100,
          ElementKey(Element.WIND, Element.WIND) to 25,
          ElementKey(Element.WIND, Element.POISON) to 100,
          ElementKey(Element.WIND, Element.HOLY) to 75,
          ElementKey(Element.WIND, Element.SHADOW) to 100,
          ElementKey(Element.WIND, Element.GHOST) to 100,
          ElementKey(Element.WIND, Element.UNDEAD) to 100,

          ElementKey(Element.POISON, Element.NORMAL) to 100,
          ElementKey(Element.POISON, Element.WATER) to 100,
          ElementKey(Element.POISON, Element.EARTH) to 125,
          ElementKey(Element.POISON, Element.FIRE) to 125,
          ElementKey(Element.POISON, Element.WIND) to 125,
          ElementKey(Element.POISON, Element.POISON) to 0,
          ElementKey(Element.POISON, Element.HOLY) to 75,
          ElementKey(Element.POISON, Element.SHADOW) to 50,
          ElementKey(Element.POISON, Element.GHOST) to 100,
          ElementKey(Element.POISON, Element.UNDEAD) to -25,

          ElementKey(Element.HOLY, Element.HOLY) to 100,
          ElementKey(Element.HOLY, Element.WATER) to 100,
          ElementKey(Element.HOLY, Element.EARTH) to 100,
          ElementKey(Element.HOLY, Element.FIRE) to 100,
          ElementKey(Element.HOLY, Element.WIND) to 100,
          ElementKey(Element.HOLY, Element.POISON) to 100,
          ElementKey(Element.HOLY, Element.HOLY) to 0,
          ElementKey(Element.HOLY, Element.SHADOW) to 125,
          ElementKey(Element.HOLY, Element.GHOST) to 100,
          ElementKey(Element.HOLY, Element.UNDEAD) to 150,

          ElementKey(Element.SHADOW, Element.NORMAL) to 100,
          ElementKey(Element.SHADOW, Element.WATER) to 100,
          ElementKey(Element.SHADOW, Element.EARTH) to 100,
          ElementKey(Element.SHADOW, Element.FIRE) to 100,
          ElementKey(Element.SHADOW, Element.WIND) to 100,
          ElementKey(Element.SHADOW, Element.POISON) to 50,
          ElementKey(Element.SHADOW, Element.HOLY) to 125,
          ElementKey(Element.SHADOW, Element.SHADOW) to 0,
          ElementKey(Element.SHADOW, Element.GHOST) to 100,
          ElementKey(Element.SHADOW, Element.UNDEAD) to -25,

          ElementKey(Element.GHOST, Element.NORMAL) to 25,
          ElementKey(Element.GHOST, Element.WATER) to 100,
          ElementKey(Element.GHOST, Element.EARTH) to 100,
          ElementKey(Element.GHOST, Element.FIRE) to 100,
          ElementKey(Element.GHOST, Element.WIND) to 100,
          ElementKey(Element.GHOST, Element.POISON) to 100,
          ElementKey(Element.GHOST, Element.HOLY) to 75,
          ElementKey(Element.GHOST, Element.SHADOW) to 75,
          ElementKey(Element.GHOST, Element.GHOST) to 125,
          ElementKey(Element.GHOST, Element.UNDEAD) to 100,

          ElementKey(Element.UNDEAD, Element.NORMAL) to 100,
          ElementKey(Element.UNDEAD, Element.WATER) to 100,
          ElementKey(Element.UNDEAD, Element.EARTH) to 100,
          ElementKey(Element.UNDEAD, Element.FIRE) to 100,
          ElementKey(Element.UNDEAD, Element.WIND) to 100,
          ElementKey(Element.UNDEAD, Element.POISON) to 50,
          ElementKey(Element.UNDEAD, Element.HOLY) to 100,
          ElementKey(Element.UNDEAD, Element.SHADOW) to 0,
          ElementKey(Element.UNDEAD, Element.GHOST) to 100,
          ElementKey(Element.UNDEAD, Element.UNDEAD) to 0,

          // LEVEL 2
          ElementKey(Element.NORMAL, Element.NORMAL_2) to 100,
          ElementKey(Element.NORMAL, Element.WATER_2) to 100,
          ElementKey(Element.NORMAL, Element.EARTH_2) to 100,
          ElementKey(Element.NORMAL, Element.FIRE_2) to 100,
          ElementKey(Element.NORMAL, Element.WIND_2) to 100,
          ElementKey(Element.NORMAL, Element.POISON_2) to 100,
          ElementKey(Element.NORMAL, Element.HOLY_2) to 100,
          ElementKey(Element.NORMAL, Element.SHADOW_2) to 100,
          ElementKey(Element.NORMAL, Element.GHOST_2) to 25,
          ElementKey(Element.NORMAL, Element.UNDEAD_2) to 100,

          ElementKey(Element.WATER, Element.NORMAL_2) to 100,
          ElementKey(Element.WATER, Element.WATER_2) to 0,
          ElementKey(Element.WATER, Element.EARTH_2) to 100,
          ElementKey(Element.WATER, Element.FIRE_2) to 175,
          ElementKey(Element.WATER, Element.WIND_2) to 25,
          ElementKey(Element.WATER, Element.POISON_2) to 100,
          ElementKey(Element.WATER, Element.HOLY_2) to 50,
          ElementKey(Element.WATER, Element.SHADOW_2) to 75,
          ElementKey(Element.WATER, Element.GHOST_2) to 100,
          ElementKey(Element.WATER, Element.UNDEAD_2) to 100,

          ElementKey(Element.EARTH, Element.NORMAL_2) to 100,
          ElementKey(Element.EARTH, Element.WATER_2) to 100,
          ElementKey(Element.EARTH, Element.EARTH_2) to 50,
          ElementKey(Element.EARTH, Element.FIRE_2) to 25,
          ElementKey(Element.EARTH, Element.WIND_2) to 175,
          ElementKey(Element.EARTH, Element.POISON_2) to 100,
          ElementKey(Element.EARTH, Element.HOLY_2) to 50,
          ElementKey(Element.EARTH, Element.SHADOW_2) to 75,
          ElementKey(Element.EARTH, Element.GHOST_2) to 100,
          ElementKey(Element.EARTH, Element.UNDEAD_2) to 100,

          ElementKey(Element.FIRE, Element.NORMAL_2) to 100,
          ElementKey(Element.FIRE, Element.WATER_2) to 25,
          ElementKey(Element.FIRE, Element.EARTH_2) to 175,
          ElementKey(Element.FIRE, Element.FIRE_2) to 0,
          ElementKey(Element.FIRE, Element.WIND_2) to 100,
          ElementKey(Element.FIRE, Element.POISON_2) to 100,
          ElementKey(Element.FIRE, Element.HOLY_2) to 50,
          ElementKey(Element.FIRE, Element.SHADOW_2) to 75,
          ElementKey(Element.FIRE, Element.GHOST_2) to 100,
          ElementKey(Element.FIRE, Element.UNDEAD_2) to 150,

          ElementKey(Element.WIND, Element.NORMAL_2) to 100,
          ElementKey(Element.WIND, Element.WATER_2) to 175,
          ElementKey(Element.WIND, Element.EARTH_2) to 25,
          ElementKey(Element.WIND, Element.FIRE_2) to 100,
          ElementKey(Element.WIND, Element.WIND_2) to 0,
          ElementKey(Element.WIND, Element.POISON_2) to 100,
          ElementKey(Element.WIND, Element.HOLY_2) to 50,
          ElementKey(Element.WIND, Element.SHADOW_2) to 75,
          ElementKey(Element.WIND, Element.GHOST_2) to 100,
          ElementKey(Element.WIND, Element.UNDEAD_2) to 100,

          ElementKey(Element.POISON, Element.NORMAL_2) to 100,
          ElementKey(Element.POISON, Element.WATER_2) to 75,
          ElementKey(Element.POISON, Element.EARTH_2) to 125,
          ElementKey(Element.POISON, Element.FIRE_2) to 125,
          ElementKey(Element.POISON, Element.WIND_2) to 125,
          ElementKey(Element.POISON, Element.POISON_2) to 0,
          ElementKey(Element.POISON, Element.HOLY_2) to 50,
          ElementKey(Element.POISON, Element.SHADOW_2) to 25,
          ElementKey(Element.POISON, Element.GHOST_2) to 75,
          ElementKey(Element.POISON, Element.UNDEAD_2) to -50,

          ElementKey(Element.HOLY, Element.HOLY_2) to 100,
          ElementKey(Element.HOLY, Element.WATER_2) to 100,
          ElementKey(Element.HOLY, Element.EARTH_2) to 100,
          ElementKey(Element.HOLY, Element.FIRE_2) to 100,
          ElementKey(Element.HOLY, Element.WIND_2) to 100,
          ElementKey(Element.HOLY, Element.POISON_2) to 100,
          ElementKey(Element.HOLY, Element.HOLY_2) to -25,
          ElementKey(Element.HOLY, Element.SHADOW_2) to 150,
          ElementKey(Element.HOLY, Element.GHOST_2) to 100,
          ElementKey(Element.HOLY, Element.UNDEAD_2) to 175,

          ElementKey(Element.SHADOW, Element.NORMAL_2) to 100,
          ElementKey(Element.SHADOW, Element.WATER_2) to 100,
          ElementKey(Element.SHADOW, Element.EARTH_2) to 100,
          ElementKey(Element.SHADOW, Element.FIRE_2) to 100,
          ElementKey(Element.SHADOW, Element.WIND_2) to 100,
          ElementKey(Element.SHADOW, Element.POISON_2) to 25,
          ElementKey(Element.SHADOW, Element.HOLY_2) to 150,
          ElementKey(Element.SHADOW, Element.SHADOW_2) to -25,
          ElementKey(Element.SHADOW, Element.GHOST_2) to 100,
          ElementKey(Element.SHADOW, Element.UNDEAD_2) to -50,

          ElementKey(Element.GHOST, Element.NORMAL_2) to 0,
          ElementKey(Element.GHOST, Element.WATER_2) to 75,
          ElementKey(Element.GHOST, Element.EARTH_2) to 75,
          ElementKey(Element.GHOST, Element.FIRE_2) to 75,
          ElementKey(Element.GHOST, Element.WIND_2) to 75,
          ElementKey(Element.GHOST, Element.POISON_2) to 75,
          ElementKey(Element.GHOST, Element.HOLY_2) to 50,
          ElementKey(Element.GHOST, Element.SHADOW_2) to 50,
          ElementKey(Element.GHOST, Element.GHOST_2) to 150,
          ElementKey(Element.GHOST, Element.UNDEAD_2) to 125,

          ElementKey(Element.UNDEAD, Element.NORMAL_2) to 100,
          ElementKey(Element.UNDEAD, Element.WATER_2) to 75,
          ElementKey(Element.UNDEAD, Element.EARTH_2) to 75,
          ElementKey(Element.UNDEAD, Element.FIRE_2) to 75,
          ElementKey(Element.UNDEAD, Element.WIND_2) to 75,
          ElementKey(Element.UNDEAD, Element.POISON_2) to 25,
          ElementKey(Element.UNDEAD, Element.HOLY_2) to 125,
          ElementKey(Element.UNDEAD, Element.SHADOW_2) to 0,
          ElementKey(Element.UNDEAD, Element.GHOST_2) to 100,
          ElementKey(Element.UNDEAD, Element.UNDEAD_2) to 0,

          // LEVEL 3
          ElementKey(Element.NORMAL, Element.NORMAL_3) to 100,
          ElementKey(Element.NORMAL, Element.WATER_3) to 100,
          ElementKey(Element.NORMAL, Element.EARTH_3) to 100,
          ElementKey(Element.NORMAL, Element.FIRE_3) to 100,
          ElementKey(Element.NORMAL, Element.WIND_3) to 100,
          ElementKey(Element.NORMAL, Element.POISON_3) to 100,
          ElementKey(Element.NORMAL, Element.HOLY_3) to 100,
          ElementKey(Element.NORMAL, Element.SHADOW_3) to 100,
          ElementKey(Element.NORMAL, Element.GHOST_3) to 0,
          ElementKey(Element.NORMAL, Element.UNDEAD_3) to 100,

          ElementKey(Element.WATER, Element.NORMAL_3) to 100,
          ElementKey(Element.WATER, Element.WATER_3) to -25,
          ElementKey(Element.WATER, Element.EARTH_3) to 100,
          ElementKey(Element.WATER, Element.FIRE_3) to 200,
          ElementKey(Element.WATER, Element.WIND_3) to 0,
          ElementKey(Element.WATER, Element.POISON_3) to 100,
          ElementKey(Element.WATER, Element.HOLY_3) to 25,
          ElementKey(Element.WATER, Element.SHADOW_3) to 50,
          ElementKey(Element.WATER, Element.GHOST_3) to 100,
          ElementKey(Element.WATER, Element.UNDEAD_3) to 125,

          ElementKey(Element.EARTH, Element.NORMAL_3) to 100,
          ElementKey(Element.EARTH, Element.WATER_3) to 100,
          ElementKey(Element.EARTH, Element.EARTH_3) to 0,
          ElementKey(Element.EARTH, Element.FIRE_3) to 0,
          ElementKey(Element.EARTH, Element.WIND_3) to 200,
          ElementKey(Element.EARTH, Element.POISON_3) to 100,
          ElementKey(Element.EARTH, Element.HOLY_3) to 25,
          ElementKey(Element.EARTH, Element.SHADOW_3) to 50,
          ElementKey(Element.EARTH, Element.GHOST_3) to 100,
          ElementKey(Element.EARTH, Element.UNDEAD_3) to 75,

          ElementKey(Element.FIRE, Element.NORMAL_3) to 100,
          ElementKey(Element.FIRE, Element.WATER_3) to 0,
          ElementKey(Element.FIRE, Element.EARTH_3) to 200,
          ElementKey(Element.FIRE, Element.FIRE_3) to -25,
          ElementKey(Element.FIRE, Element.WIND_3) to 100,
          ElementKey(Element.FIRE, Element.POISON_3) to 100,
          ElementKey(Element.FIRE, Element.HOLY_3) to 25,
          ElementKey(Element.FIRE, Element.SHADOW_3) to 50,
          ElementKey(Element.FIRE, Element.GHOST_3) to 100,
          ElementKey(Element.FIRE, Element.UNDEAD_3) to 175,

          ElementKey(Element.WIND, Element.NORMAL_3) to 100,
          ElementKey(Element.WIND, Element.WATER_3) to 200,
          ElementKey(Element.WIND, Element.EARTH_3) to 0,
          ElementKey(Element.WIND, Element.FIRE_3) to 100,
          ElementKey(Element.WIND, Element.WIND_3) to -25,
          ElementKey(Element.WIND, Element.POISON_3) to 100,
          ElementKey(Element.WIND, Element.HOLY_3) to 25,
          ElementKey(Element.WIND, Element.SHADOW_3) to 50,
          ElementKey(Element.WIND, Element.GHOST_3) to 100,
          ElementKey(Element.WIND, Element.UNDEAD_3) to 100,

          ElementKey(Element.POISON, Element.NORMAL_3) to 100,
          ElementKey(Element.POISON, Element.WATER_3) to 50,
          ElementKey(Element.POISON, Element.EARTH_3) to 100,
          ElementKey(Element.POISON, Element.FIRE_3) to 100,
          ElementKey(Element.POISON, Element.WIND_3) to 100,
          ElementKey(Element.POISON, Element.POISON_3) to 0,
          ElementKey(Element.POISON, Element.HOLY_3) to 25,
          ElementKey(Element.POISON, Element.SHADOW_3) to 0,
          ElementKey(Element.POISON, Element.GHOST_3) to 50,
          ElementKey(Element.POISON, Element.UNDEAD_3) to -75,

          ElementKey(Element.HOLY, Element.HOLY_3) to 100,
          ElementKey(Element.HOLY, Element.WATER_3) to 100,
          ElementKey(Element.HOLY, Element.EARTH_3) to 100,
          ElementKey(Element.HOLY, Element.FIRE_3) to 100,
          ElementKey(Element.HOLY, Element.WIND_3) to 100,
          ElementKey(Element.HOLY, Element.POISON_3) to 125,
          ElementKey(Element.HOLY, Element.HOLY_3) to -50,
          ElementKey(Element.HOLY, Element.SHADOW_3) to 175,
          ElementKey(Element.HOLY, Element.GHOST_3) to 100,
          ElementKey(Element.HOLY, Element.UNDEAD_3) to 200,

          ElementKey(Element.SHADOW, Element.NORMAL_3) to 100,
          ElementKey(Element.SHADOW, Element.WATER_3) to 100,
          ElementKey(Element.SHADOW, Element.EARTH_3) to 100,
          ElementKey(Element.SHADOW, Element.FIRE_3) to 100,
          ElementKey(Element.SHADOW, Element.WIND_3) to 100,
          ElementKey(Element.SHADOW, Element.POISON_3) to 0,
          ElementKey(Element.SHADOW, Element.HOLY_3) to 175,
          ElementKey(Element.SHADOW, Element.SHADOW_3) to -50,
          ElementKey(Element.SHADOW, Element.GHOST_3) to 100,
          ElementKey(Element.SHADOW, Element.UNDEAD_3) to -75,

          ElementKey(Element.GHOST, Element.NORMAL_3) to 0,
          ElementKey(Element.GHOST, Element.WATER_3) to 50,
          ElementKey(Element.GHOST, Element.EARTH_3) to 50,
          ElementKey(Element.GHOST, Element.FIRE_3) to 50,
          ElementKey(Element.GHOST, Element.WIND_3) to 50,
          ElementKey(Element.GHOST, Element.POISON_3) to 50,
          ElementKey(Element.GHOST, Element.HOLY_3) to 25,
          ElementKey(Element.GHOST, Element.SHADOW_3) to 25,
          ElementKey(Element.GHOST, Element.GHOST_3) to 175,
          ElementKey(Element.GHOST, Element.UNDEAD_3) to 150,

          ElementKey(Element.UNDEAD, Element.NORMAL_3) to 100,
          ElementKey(Element.UNDEAD, Element.WATER_3) to 50,
          ElementKey(Element.UNDEAD, Element.EARTH_3) to 50,
          ElementKey(Element.UNDEAD, Element.FIRE_3) to 50,
          ElementKey(Element.UNDEAD, Element.WIND_3) to 50,
          ElementKey(Element.UNDEAD, Element.POISON_3) to 0,
          ElementKey(Element.UNDEAD, Element.HOLY_3) to 150,
          ElementKey(Element.UNDEAD, Element.SHADOW_3) to 0,
          ElementKey(Element.UNDEAD, Element.GHOST_3) to 100,
          ElementKey(Element.UNDEAD, Element.UNDEAD_3) to 0,

          // LEVEL 4
          ElementKey(Element.NORMAL, Element.NORMAL_4) to 100,
          ElementKey(Element.NORMAL, Element.WATER_4) to 100,
          ElementKey(Element.NORMAL, Element.EARTH_4) to 100,
          ElementKey(Element.NORMAL, Element.FIRE_4) to 100,
          ElementKey(Element.NORMAL, Element.WIND_4) to 100,
          ElementKey(Element.NORMAL, Element.POISON_4) to 100,
          ElementKey(Element.NORMAL, Element.HOLY_4) to 100,
          ElementKey(Element.NORMAL, Element.SHADOW_4) to 100,
          ElementKey(Element.NORMAL, Element.GHOST_4) to 0,
          ElementKey(Element.NORMAL, Element.UNDEAD_4) to 100,

          ElementKey(Element.WATER, Element.NORMAL_4) to 100,
          ElementKey(Element.WATER, Element.WATER_4) to -50,
          ElementKey(Element.WATER, Element.EARTH_4) to 100,
          ElementKey(Element.WATER, Element.FIRE_4) to 200,
          ElementKey(Element.WATER, Element.WIND_4) to 0,
          ElementKey(Element.WATER, Element.POISON_4) to 75,
          ElementKey(Element.WATER, Element.HOLY_4) to 0,
          ElementKey(Element.WATER, Element.SHADOW_4) to 25,
          ElementKey(Element.WATER, Element.GHOST_4) to 100,
          ElementKey(Element.WATER, Element.UNDEAD_4) to 150,

          ElementKey(Element.EARTH, Element.NORMAL_4) to 100,
          ElementKey(Element.EARTH, Element.WATER_4) to 100,
          ElementKey(Element.EARTH, Element.EARTH_4) to -25,
          ElementKey(Element.EARTH, Element.FIRE_4) to 0,
          ElementKey(Element.EARTH, Element.WIND_4) to 200,
          ElementKey(Element.EARTH, Element.POISON_4) to 75,
          ElementKey(Element.EARTH, Element.HOLY_4) to 0,
          ElementKey(Element.EARTH, Element.SHADOW_4) to 25,
          ElementKey(Element.EARTH, Element.GHOST_4) to 100,
          ElementKey(Element.EARTH, Element.UNDEAD_4) to 50,

          ElementKey(Element.FIRE, Element.NORMAL_4) to 100,
          ElementKey(Element.FIRE, Element.WATER_4) to 0,
          ElementKey(Element.FIRE, Element.EARTH_4) to 200,
          ElementKey(Element.FIRE, Element.FIRE_4) to -50,
          ElementKey(Element.FIRE, Element.WIND_4) to 100,
          ElementKey(Element.FIRE, Element.POISON_4) to 75,
          ElementKey(Element.FIRE, Element.HOLY_4) to 0,
          ElementKey(Element.FIRE, Element.SHADOW_4) to 25,
          ElementKey(Element.FIRE, Element.GHOST_4) to 100,
          ElementKey(Element.FIRE, Element.UNDEAD_4) to 200,

          ElementKey(Element.WIND, Element.NORMAL_4) to 100,
          ElementKey(Element.WIND, Element.WATER_4) to 200,
          ElementKey(Element.WIND, Element.EARTH_4) to 0,
          ElementKey(Element.WIND, Element.FIRE_4) to 100,
          ElementKey(Element.WIND, Element.WIND_4) to -50,
          ElementKey(Element.WIND, Element.POISON_4) to 75,
          ElementKey(Element.WIND, Element.HOLY_4) to 0,
          ElementKey(Element.WIND, Element.SHADOW_4) to 25,
          ElementKey(Element.WIND, Element.GHOST_4) to 100,
          ElementKey(Element.WIND, Element.UNDEAD_4) to 100,

          ElementKey(Element.POISON, Element.NORMAL_4) to 100,
          ElementKey(Element.POISON, Element.WATER_4) to 25,
          ElementKey(Element.POISON, Element.EARTH_4) to 75,
          ElementKey(Element.POISON, Element.FIRE_4) to 75,
          ElementKey(Element.POISON, Element.WIND_4) to 75,
          ElementKey(Element.POISON, Element.POISON_4) to 0,
          ElementKey(Element.POISON, Element.HOLY_4) to 0,
          ElementKey(Element.POISON, Element.SHADOW_4) to -25,
          ElementKey(Element.POISON, Element.GHOST_4) to 25,
          ElementKey(Element.POISON, Element.UNDEAD_4) to -100,

          ElementKey(Element.HOLY, Element.HOLY_4) to 100,
          ElementKey(Element.HOLY, Element.WATER_4) to 75,
          ElementKey(Element.HOLY, Element.EARTH_4) to 75,
          ElementKey(Element.HOLY, Element.FIRE_4) to 75,
          ElementKey(Element.HOLY, Element.WIND_4) to 75,
          ElementKey(Element.HOLY, Element.POISON_4) to 125,
          ElementKey(Element.HOLY, Element.HOLY_4) to -100,
          ElementKey(Element.HOLY, Element.SHADOW_4) to 200,
          ElementKey(Element.HOLY, Element.GHOST_4) to 100,
          ElementKey(Element.HOLY, Element.UNDEAD_4) to 200,

          ElementKey(Element.SHADOW, Element.NORMAL_4) to 100,
          ElementKey(Element.SHADOW, Element.WATER_4) to 75,
          ElementKey(Element.SHADOW, Element.EARTH_4) to 75,
          ElementKey(Element.SHADOW, Element.FIRE_4) to 75,
          ElementKey(Element.SHADOW, Element.WIND_4) to 75,
          ElementKey(Element.SHADOW, Element.POISON_4) to -25,
          ElementKey(Element.SHADOW, Element.HOLY_4) to 200,
          ElementKey(Element.SHADOW, Element.SHADOW_4) to -100,
          ElementKey(Element.SHADOW, Element.GHOST_4) to 100,
          ElementKey(Element.SHADOW, Element.UNDEAD_4) to -100,

          ElementKey(Element.GHOST, Element.NORMAL_4) to 0,
          ElementKey(Element.GHOST, Element.WATER_4) to 25,
          ElementKey(Element.GHOST, Element.EARTH_4) to 25,
          ElementKey(Element.GHOST, Element.FIRE_4) to 25,
          ElementKey(Element.GHOST, Element.WIND_4) to 25,
          ElementKey(Element.GHOST, Element.POISON_4) to 25,
          ElementKey(Element.GHOST, Element.HOLY_4) to 0,
          ElementKey(Element.GHOST, Element.SHADOW_4) to 0,
          ElementKey(Element.GHOST, Element.GHOST_4) to 200,
          ElementKey(Element.GHOST, Element.UNDEAD_4) to 175,

          ElementKey(Element.UNDEAD, Element.NORMAL_4) to 100,
          ElementKey(Element.UNDEAD, Element.WATER_4) to 25,
          ElementKey(Element.UNDEAD, Element.EARTH_4) to 25,
          ElementKey(Element.UNDEAD, Element.FIRE_4) to 25,
          ElementKey(Element.UNDEAD, Element.WIND_4) to 25,
          ElementKey(Element.UNDEAD, Element.POISON_4) to -25,
          ElementKey(Element.UNDEAD, Element.HOLY_4) to 175,
          ElementKey(Element.UNDEAD, Element.SHADOW_4) to 0,
          ElementKey(Element.UNDEAD, Element.GHOST_4) to 100,
          ElementKey(Element.UNDEAD, Element.UNDEAD_4) to 0
  )

  private val legalAttackElements = EnumSet.of(
          Element.NORMAL,
          Element.WATER,
          Element.EARTH,
          Element.FIRE,
          Element.WIND,
          Element.POISON,
          Element.HOLY,
          Element.SHADOW,
          Element.GHOST,
          Element.UNDEAD
  )

  /**
   * Wraps the creation of element keys to retrieve the element modifier.
   *
   * @author Thomas Felix
   */
  private data class ElementKey(
    private val el1: Element,
    private val el2: Element
  )

  /**
   * Returns the damage modifier for a given attacker element and defender
   * element. Attack element must always be of level 1.
   *
   * @param attacker
   * Element of the attacker.
   * @param defender
   * Element of the defender.
   * @return The damage modifier.
   */
  fun getModifier(attacker: Element, defender: Element): Int {
    if (!legalAttackElements.contains(attacker)) {
      throw IllegalArgumentException("Attack element must always be level 1.")
    }

    val key = ElementKey(attacker, defender)
    return elementMap[key] ?: 100
  }

  /**
   * Alias of [.getModifier] but returns the value as
   * a float value (e.g. 1.25 instead of 125).
   *
   * @param attacker
   * Element of the attacker. Must be level 1 element.
   * @param defender
   * Element of the defender.
   * @return The damage modifier.
   */
  fun getModifierFloat(attacker: Element, defender: Element): Float {
    return getModifier(attacker, defender) / 100f
  }
}
