package net.bestia.zone.cartography

import net.bestia.zone.cartography.tile.MapWorldKey
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tuning for surveying and for the charts it produces.
 *
 * Only the numbers that are genuinely balance knobs live here. The survey *cell* deliberately does not - see
 * [net.bestia.zone.cartography.coverage.SurveyGrid.CELL_METRES], which is positional in every stored chart and
 * cannot be changed without reinterpreting them.
 *
 * @property surveyRadiusPerLevelMetres radius a survey charts, multiplied by the caster's rank in
 *   `CARTOGRAPHY`. The skill caps at rank 5 (`master_skill_tree.yml`), so the default gives a kilometre at
 *   rank 1 up to five at rank 5 - the range the design asks for, and the reason the ladder is a plain
 *   multiple rather than a base plus a gain.
 * @property starterChartRadiusMetres radius of the chart a new master is created holding. Charts are the only
 *   source of map knowledge, so without this a fresh master's map and minimap are blank - see
 *   `MasterFactory`. Sized to show the home settlement and the ground around it rather than to be a head start.
 * @property cacheDir root of the on-disk tile cache. One directory per [MapWorldKey] beneath it, so deleting the
 *   lot costs render time and nothing else - tiles are a pure function of the world and the styles.
 */
@ConfigurationProperties("cartography")
data class CartographyConfig(
  val surveyRadiusPerLevelMetres: Double,
  val starterChartRadiusMetres: Double,
  val cacheDir: String,
)
