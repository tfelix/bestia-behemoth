package net.bestia.zone.environment.weather

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What counts as comfortable, and what being outside it costs.
 *
 * The band is wide on purpose. `REMINDER.md` asks that the low-level country stay comfortable all year and that
 * the deserts, mountains and high-mana provinces be the places needing equipment - so exposure has to be a
 * property of the *hostile* parts of the map rather than a tax on being outdoors.
 */
@ConfigurationProperties("weather.exposure")
data class ExposureConfig(

  /** Set false to disable exposure entirely; weather is still evaluated and still sent to clients. */
  val enabled: Boolean = true,

  /** Real seconds between exposure ticks. */
  val intervalSeconds: Float = 5f,

  /**
   * Coldest felt temperature that costs nothing.
   *
   * **Measured, not chosen.** `LocalTemperatureTest` samples the reference world eight times a day at eight
   * points in the year and reports the share of the year each kind of country spends outside a candidate band:
   *
   * | band | gentle country | harsh country |
   * | --- | --- | --- |
   * | -2 C | 11.0% | 86.7% |
   * | **-6 C** | **4.7%** | **71.2%** |
   * | -10 C | 1.7% | 47.9% |
   *
   * -6 is the balance the brief asks for: the low-level country is comfortable "more or less the whole year"
   * with a handful of winter nights that *teach* the mechanic somewhere survivable, while the deserts, the
   * alpine ground and the ice are outside it most of the time and genuinely need equipment. At -2 a winter
   * night in a starter forest costs stamina, which is a tax on ordinary travel; at -10 the ice sheet is
   * comfortable half the year.
   */
  val comfortLowCelsius: Double = -6.0,

  /**
   * Warmest felt temperature that costs nothing.
   *
   * **Currently unreachable, and that is worth knowing rather than discovering.** The reference world never
   * exceeds 34 C anywhere, at any hour, in any season - moving this to 36 changed the measured share by
   * exactly nothing - so the *heat* half of exposure does not fire today. It is not dead code: `REMINDER.md`
   * plans volcanic regions and lava wells, and those are what will make it fire. Until then, only cold bites.
   */
  val comfortHighCelsius: Double = 34.0,

  /** Stamina lost per tick per degree outside the band. */
  val staminaPerDegree: Double = 0.6,

  /** Health lost per point of stamina cost, once stamina is exhausted. */
  val healthShare: Double = 0.5,

  /**
   * Degrees the comfort band widens per level of `WEATHER_RESISTANCE`, at both ends.
   *
   * Three, so ten levels move the cold end from -6 to -36 C - which on the reference world is the difference
   * between "the ice sheet is lethal" and "the ice sheet is a place you can work". Widening the band rather
   * than reducing the damage is deliberate; see the system.
   */
  val tolerancePerResistanceLevel: Double = 3.0
) {
  init {
    require(comfortLowCelsius < comfortHighCelsius) { "the comfort band is inverted" }
    require(intervalSeconds > 0f) { "intervalSeconds must be positive" }
    require(staminaPerDegree > 0.0) { "staminaPerDegree must be positive" }
    require(healthShare > 0.0) { "healthShare must be positive" }
    require(tolerancePerResistanceLevel >= 0.0) { "tolerancePerResistanceLevel must not be negative" }
  }
}
