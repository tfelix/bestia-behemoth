using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// How the wind gets from what it was to what the server just said.
  /// </summary>
  /// <remarks>
  /// Two halves on two clocks, which is the whole reason this is not one call to a vector lerp. How hard the
  /// wind is blowing and which way it is blowing are different events to watch: a gust arrives over a few
  /// seconds and the player sees it arrive, while the bearing backing round is weather rather than an event -
  /// and <c>cloud_shadows.gd</c> drifts its mask on that bearing, so a wind that turned as fast as it rose
  /// would wheel the whole shadow field across the sky.
  ///
  /// <para>
  /// <b>Polar rather than componentwise, and that fixes a second fault as well as buying the split.</b> Lerping
  /// the vector takes the straight line between two arrows, which passes near the origin whenever the wind
  /// reverses - so a wind swinging round from west to east died to a dead calm halfway and the meadow stood
  /// upright for a second in the middle of a gale. It also cannot separate the two clocks at all: one lerp has
  /// one constant. Turning through <see cref="Vector3.SignedAngleTo"/> keeps the speed out of the bearing's
  /// arithmetic entirely, and takes the short way round without ever meeting the radian wrap that made the
  /// componentwise form attractive in the first place.
  /// </para>
  ///
  /// <para>
  /// Static and free of every Godot node type on purpose, the same split <see cref="GrassLod"/> takes: this is
  /// arithmetic with a property worth pinning, and arithmetic left inside <see cref="WeatherState"/>'s
  /// <c>_Process</c> could only be judged by eye.
  /// </para>
  /// </remarks>
  public static class WindSmoothing
  {
    /// <summary>
    /// Below this a wind has no bearing worth keeping, in metres per second.
    /// </summary>
    /// <remarks>
    /// Far below anything the server sends - <c>WeatherModel</c>'s floor is 3 m/s - so this catches only the
    /// two vectors that are genuinely zero rather than small: <c>_wind</c> before the first message, and a
    /// target built from a debug speed of nought.
    /// </remarks>
    private const float Calm = 0.0001f;

    /// <summary>
    /// <paramref name="wind"/> eased toward <paramref name="target"/>, its speed and its bearing on their own
    /// time constants.
    /// </summary>
    /// <remarks>
    /// Framerate independent in both halves, and by construction rather than approximately: the bearing is
    /// re-measured on every call, but the angle still remaining decays by the same factor however the second is
    /// cut up, so sixty steps of a sixtieth land where one step of a second does to a part in a hundred
    /// thousand of float32. A plain lerp by <paramref name="delta"/> is out by percent.
    /// </remarks>
    public static Vector3 Approach(
      Vector3 wind, Vector3 target, float delta, float speedSeconds, float bearingSeconds)
    {
      var was = wind.Length();
      var wants = target.Length();

      var speed = Mathf.Lerp(was, wants, Fraction(delta, speedSeconds));

      // A dead calm has no bearing of its own, so it takes the one it is being handed.
      if (was <= Calm)
      {
        return wants <= Calm ? Vector3.Zero : target.Normalized() * speed;
      }

      // The mirror: a wind falling away to nothing has no bearing to aim at, so it keeps its own and only slows.
      if (wants <= Calm)
      {
        return wind.Normalized() * speed;
      }

      var turn = wind.SignedAngleTo(target, Vector3.Up) * Fraction(delta, bearingSeconds);

      return wind.Normalized().Rotated(Vector3.Up, turn) * speed;
    }

    /// <summary>How much of the way to the target one step of <paramref name="delta"/> covers, in [0, 1].</summary>
    private static float Fraction(float delta, float seconds)
    {
      return 1.0f - Mathf.Exp(-delta / Mathf.Max(seconds, 0.001f));
    }
  }
}
