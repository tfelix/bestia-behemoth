package net.bestia.zone.cartography.survey

/**
 * How wide a survey reaches, and how hard it is before distance and mana are counted - the docs' three tiers. A
 * master picks one when casting Cartography; the level decides which are open.
 */
enum class SurveyTier(val radiusMetres: Int, val baseDifficulty: Int, val minLevel: Int) {
  KM_1(1000, 30, 1),
  KM_3(3000, 50, 3),
  KM_5(5000, 80, 5);

  companion object {
    /** The tier a cast asked for by its kilometres, or null for a number that is no tier. */
    fun ofKilometres(km: Long?): SurveyTier? = entries.firstOrNull { it.radiusMetres / 1000L == km }
  }
}
