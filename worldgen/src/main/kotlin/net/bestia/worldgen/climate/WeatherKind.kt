package net.bestia.worldgen.climate

/**
 * What the sky is doing.
 *
 * Eleven values, and two of the choices are worth recording.
 *
 * **`BLIZZARD` is here though nothing asked for it.** It is the cold analogue of [HEAVY_RAIN] and of
 * [SANDSTORM], and it is the obvious extreme event of the polar and alpine regions. Without it the ladder in
 * `WeatherField` is asymmetric in a way that invites an `else`.
 *
 * **[RAIN] and [HEAVY_RAIN] stay distinct kinds** even though intensity is a float beside them. Client visuals
 * and resistance checks key on the *kind*, and a threshold applied at eleven call sites is a threshold that
 * will eventually disagree with itself.
 *
 * A `when` over this enum as a **subject** - the temperature modifier, a client visual table - must be
 * exhaustive with no `else`, for `voxel/SurfaceCover`'s reason: a default is an answer given on behalf of a
 * case nobody has thought about, and the failure is the plausible kind. The priority ladder in `WeatherField`
 * is the other shape - it has no subject, so its trailing `CLEAR` is a real default.
 */
enum class WeatherKind(val label: String) {
  CLEAR("clear"),
  CLOUDY("cloudy"),
  FOG("fog"),
  RAIN("rain"),
  HEAVY_RAIN("heavy rain"),
  THUNDERSTORM("thunderstorm"),
  SNOW("snow"),
  BLIZZARD("blizzard"),
  SANDSTORM("sandstorm"),

  /**
   * The endgame weather, and mechanically distinct rather than a reskinned thunderstorm.
   *
   * It needs no precipitation, so it can stand in a clear sky over a desert. It **ignores shelter**, so it is
   * the only weather that happens underground in a high-mana cave - which gives a roof a cost and makes
   * `WEATHER_RESISTANCE` worth buying. And it carries a temperature anomaly of *either* sign, which is what
   * `REMINDER.md`'s "high mana means more extreme swings" asks for, delivered literally.
   */
  MANA_STORM("mana storm"),

  /**
   * A tornado is on the ground **somewhere in this region** - the only honest statement a sixteen-kilometre
   * cell can make about one.
   *
   * Where exactly is a separate O(1) question; see `WeatherField.hazardAt`. Shipping the kind without the
   * point would leave a status line nobody can run from.
   */
  TORNADO("tornado");

  /**
   * True where the kind carries falling water or snow.
   *
   * [TORNADO] counts: it is drawn out of a thunderstorm and the rain does not stop because the wind got
   * organised. [MANA_STORM] does not, because it needs no precipitation at all and routinely happens in a
   * clear sky.
   */
  val precipitating: Boolean
    get() = this == RAIN || this == HEAVY_RAIN || this == THUNDERSTORM ||
        this == SNOW || this == BLIZZARD || this == TORNADO

  /** True where shelter does not help. See [MANA_STORM]. */
  val ignoresShelter: Boolean get() = this == MANA_STORM
}
