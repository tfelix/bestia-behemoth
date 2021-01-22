package net.bestia.zoneserver.environment

import net.bestia.model.geometry.Vec2

/**
 * Calculates the weather in certain areas. The calculation is that the target weather condition for a cell
 * is evaluated every 10 minutes. After that the current condition is linearly interpolated towards the target condition
 * every 10 seconds.
 */
class WeatherService {
  private data class WeatherParams(
      var x: Long,
      var y: Long,
      var humidity: Float,
      var temperature: Float,
      var cloudiness: Float,
      var rain: Float,
      var wind: Vec2 = Vec2(0, 0),
      var temperatureContribution: Float = 0f
  )

  private class Layer()

  fun getGroundTemperatureInCell(x: Long, y: Long): Float {
    return 25f
  }

  fun calculateWeatherInCell(x: Long, y: Long) {
    // Übername von soll nach ist params der Zelle.
    // calculate tGround by time of day, season, biome, distance to equator, cloudiness/rain and neighbour influence
    // calculate tSky by time of day, season
    val tGround = 30f
    val tSky = 10f
    val windStrength = 10f
    // abhängig davon wie viel wasser in dem bereich ist. 100% = 100%. Ansonsten steigern und senken spieler spells
    // das level (feuer vs wasser)
    val gHumid = 50 // 0-100

    // last values are from the last calculation
    val hLayers = mutableListOf(50f, 10f, 10f)

    val tLayers = listOf(tGround, (tGround + tSky) / 2, tSky)
    val tEvap = tLayers.map { t -> evaporationRate(t, windStrength) }

    // for each layer evaporation rate is calculated
    val dEvap = listOf(hLayers[0] * tEvap[0], hLayers[1] * tEvap[1])
    val dE1 = dEvap[0] * hLayers[0]
    val dE2 = dEvap[1] * hLayers[1]

    hLayers[0] = hLayers[0] - dE1
    hLayers[1] = hLayers[1] + dE1 - dE2
    hLayers[2] = hLayers[2] + dE2

    // berechnen ob aufgrund der t es nun zu nebel kommt, wenn humdGround > humidGroundMax
    // berechnen ob aufgrund der t es nun zu wolken kommt, wenn humdSky > humidClearSkyMax
    // berechnen ob aufgrund der t es nun zu regen/schnee kommt, wenn humdSky > humidRainSkyMax

    // berechnen
    // temperatur gradient zu den nachbarn berechnen und hinzufügen, wind weht von kalt nach warm

    // 0.1 der temperatur ist in die zelle hinzugefügt in die der wind weht.

    // abspeichern von gHumid, tGround, windrichtung, foggyness, cloudiness, raininess
  }

  private fun evaporationRate(t: Float, windStrength: Float): Float {
    return ((97.0 / 100.0 * t + 3) + (windStrength * 2 / 100.0)).toFloat()
  }
}