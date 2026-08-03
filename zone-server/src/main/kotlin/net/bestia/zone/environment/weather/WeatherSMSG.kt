package net.bestia.zone.environment.weather

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WeatherProto
import net.bestia.bnet.proto.WeatherSMSGProto
import net.bestia.worldgen.climate.Temperature
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.climate.WeatherState
import net.bestia.zone.message.SMSG

/**
 * The weather where one player is standing.
 *
 * Carries no region geometry - see the `.proto` for why - and no world seed, for the reason `WorldInfoSMSG`
 * carries none either.
 */
data class WeatherSMSG(
  val regionId: Int,
  val kind: WeatherKind,
  val intensity: Double,
  val cloudCover: Double,
  val windSpeed: Double,
  val windDirection: Double,
  val temperatureCelsius: Double,
  val feltTemperatureCelsius: Double,
  val hazardX: Long? = null,
  val hazardY: Long? = null,
  val hazardRadiusMetres: Double = 0.0,

  /** What WEATHER_SENSE saw coming, or null when the player has no such skill or nothing changes. */
  val forecast: Forecast? = null
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val weather = WeatherSMSGProto.WeatherSMSG.newBuilder()
      .setRegionId(regionId)
      .setKind(wireKindOf(kind))
      .setIntensity(intensity.toFloat())
      .setCloudCover(cloudCover.toFloat())
      .setWindSpeed(windSpeed.toFloat())
      .setWindDirection(windDirection.toFloat())
      .setTemperatureCelsius(temperatureCelsius.toFloat())
      .setFeltTemperatureCelsius(feltTemperatureCelsius.toFloat())

    forecast?.let {
      weather
        .setHasForecast(true)
        .setForecastKind(wireKindOf(it.kind))
        .setForecastInSeconds(it.inRealSeconds)
    }

    if (hazardX != null && hazardY != null) {
      weather
        .setHasHazard(true)
        .setHazardX(hazardX)
        .setHazardY(hazardY)
        .setHazardRadiusMetres(hazardRadiusMetres.toFloat())
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setWeather(weather.build())
      .build()
  }

  companion object {

    /**
     * Builds a message from a generator state, a temperature and the voxel position of the hazard.
     *
     * The conversion lives here rather than in the service so the two enums meet in exactly one place.
     */
    fun of(
      regionId: Int,
      state: WeatherState,
      temperature: Temperature,
      hazardVoxel: Pair<Long, Long>? = null,
      forecast: Forecast? = null
    ) = WeatherSMSG(
      regionId = regionId,
      kind = state.kind,
      intensity = state.intensity,
      cloudCover = state.cloudCover,
      windSpeed = state.windSpeed,
      windDirection = state.windDirection,
      temperatureCelsius = temperature.airCelsius,
      feltTemperatureCelsius = temperature.feelsLikeCelsius,
      hazardX = hazardVoxel?.first,
      hazardY = hazardVoxel?.second,
      hazardRadiusMetres = state.hazard?.radiusMetres ?: 0.0,
      forecast = forecast
    )

    /**
     * Generator kind to wire kind.
     *
     * An exhaustive `when` with **no `else`**, so a kind added to `WeatherKind` is a compile error here until
     * somebody adds the wire value and bumps nothing - a new kind a client cannot name would otherwise be
     * silently sent as clear sky, which is the plausible-looking failure `voxel/SurfaceCover` argues against.
     */
    private fun wireKindOf(kind: WeatherKind): WeatherProto.WeatherKind = when (kind) {
      WeatherKind.CLEAR -> WeatherProto.WeatherKind.WEATHER_CLEAR
      WeatherKind.CLOUDY -> WeatherProto.WeatherKind.WEATHER_CLOUDY
      WeatherKind.FOG -> WeatherProto.WeatherKind.WEATHER_FOG
      WeatherKind.RAIN -> WeatherProto.WeatherKind.WEATHER_RAIN
      WeatherKind.HEAVY_RAIN -> WeatherProto.WeatherKind.WEATHER_HEAVY_RAIN
      WeatherKind.THUNDERSTORM -> WeatherProto.WeatherKind.WEATHER_THUNDERSTORM
      WeatherKind.SNOW -> WeatherProto.WeatherKind.WEATHER_SNOW
      WeatherKind.BLIZZARD -> WeatherProto.WeatherKind.WEATHER_BLIZZARD
      WeatherKind.SANDSTORM -> WeatherProto.WeatherKind.WEATHER_SANDSTORM
      WeatherKind.MANA_STORM -> WeatherProto.WeatherKind.WEATHER_MANA_STORM
      WeatherKind.TORNADO -> WeatherProto.WeatherKind.WEATHER_TORNADO
    }
  }
}
