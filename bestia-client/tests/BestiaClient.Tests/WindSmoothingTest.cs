using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// How the wind gets from one reading to the next: its speed and its bearing, on their own clocks.
  /// </summary>
  /// <remarks>
  /// Written against two faults of the componentwise vector lerp this replaced. It could not separate the two
  /// clocks - one lerp has one constant, so a gust took as long to arrive as the bearing took to back round -
  /// and the straight line between two opposed arrows passes through the origin, so a reversing wind died to a
  /// dead calm halfway and the meadow stood upright in the middle of a gale.
  ///
  /// <para>
  /// The arithmetic lives in <see cref="WindSmoothing"/> rather than in <c>WeatherState._Process</c> precisely
  /// so it can be pinned here: <c>WeatherState</c> is a <c>Node</c> with a per-frame callback and this project
  /// runs without an engine.
  /// </para>
  ///
  /// <para>
  /// <b>Every angle below is unsigned, and that is not laziness.</b> The server's bearing turns from +X toward
  /// +Z (see <c>WeatherState.Wind</c>), while Godot's rotation about <c>Vector3.Up</c> takes +X toward -Z - so
  /// a rising server bearing is a falling Godot angle. <see cref="WindSmoothing"/> is unaffected, because
  /// <c>SignedAngleTo</c> and <c>Rotated</c> share the convention and it cancels between them; a test that
  /// picked a sign would be pinning which of the two it happened to be written against.
  /// </para>
  /// </remarks>
  public class WindSmoothingTest
  {
    private const float SpeedSeconds = 3.0f;
    private const float BearingSeconds = 12.0f;

    private const float Frame = 1.0f / 60.0f;

    /// <summary>A wind of <paramref name="speed"/> on a bearing of <paramref name="degrees"/>, east at zero.</summary>
    /// <remarks>The same construction <c>WeatherState.TargetWind</c> makes from the server's two numbers.</remarks>
    private static Vector3 Wind(float speed, float degrees)
    {
      var radians = Mathf.DegToRad(degrees);

      return new Vector3(Mathf.Cos(radians), 0.0f, Mathf.Sin(radians)) * speed;
    }

    /// <summary>The angle between two winds, in degrees, always the short way and never signed.</summary>
    private static float Between(Vector3 from, Vector3 to)
    {
      return Mathf.RadToDeg(Mathf.Abs(from.SignedAngleTo(to, Vector3.Up)));
    }

    /// <summary>
    /// A reversing wind never slackens below the weaker of the two it is between.
    /// </summary>
    /// <remarks>
    /// <b>The fault, stated as a property.</b> Where the bearing ends up is a matter of taste; that the wind
    /// does not stop blowing on the way there is not. The vector lerp this replaced fell to under a metre a
    /// second halfway between these two. 170 degrees rather than 180 because a perfect reversal has no short
    /// way round to prefer - the case worth pinning is the one the server produces, a veer of up to 1.8 rad
    /// landing on top of a prevailing bearing.
    /// </remarks>
    [Fact]
    public void AReversingWindNeverFallsCalm()
    {
      var to = Wind(11.0f, 175.0f);
      var wind = Wind(9.0f, 5.0f);

      // Twelve time constants of the bearing, which is what a 170 degree turn takes to finish.
      for (var elapsed = 0.0f; elapsed < 150.0f; elapsed += Frame)
      {
        wind = WindSmoothing.Approach(wind, to, Frame, SpeedSeconds, BearingSeconds);

        Assert.True(
          wind.Length() >= 9.0f - 0.001f,
          $"at {elapsed:0.00} s the wind had fallen to {wind.Length():0.000} m/s between two of 9 and 11");
      }

      Assert.InRange(Between(wind, to), 0.0f, 0.01f);
      Assert.InRange(wind.Length(), 11.0f - 0.001f, 11.0f + 0.001f);
    }

    /// <summary>The bearing takes the short way round, and only ever that way.</summary>
    /// <remarks>
    /// Monotone as well as short: a turn that overshot and came back would satisfy a test on the endpoints
    /// alone. These two straddle due west, which is where the server's radians wrap - the crossing that makes a
    /// naive angle lerp unusable, and that the componentwise form was originally chosen to avoid.
    /// </remarks>
    [Fact]
    public void TheBearingTurnsTheShortWayRound()
    {
      var from = Wind(8.0f, 170.0f);
      var to = Wind(8.0f, -170.0f);

      var wind = from;
      var previous = 0.0f;

      for (var elapsed = 0.0f; elapsed < 60.0f; elapsed += Frame)
      {
        wind = WindSmoothing.Approach(wind, to, Frame, SpeedSeconds, BearingSeconds);

        var turned = Between(from, wind);

        Assert.True(turned >= previous - 0.001f, $"at {elapsed:0.00} s the turn went back on itself");
        Assert.True(
          turned <= 20.0f + 0.001f,
          $"at {elapsed:0.00} s the wind had turned {turned:0.0} degrees, so it went the long way");

        previous = turned;
      }
    }

    /// <summary>
    /// Speed and bearing are on their own clocks, and the speed's is the shorter one.
    /// </summary>
    /// <remarks>
    /// The point of the split: at one time constant of the speed the gust is most of the way in, while the
    /// bearing - four times slower - has barely started. One lerp cannot produce this, so this fails if anyone
    /// puts one back.
    /// </remarks>
    [Fact]
    public void TheSpeedArrivesWellBeforeTheBearing()
    {
      var from = Wind(4.0f, 0.0f);
      var to = Wind(20.0f, 90.0f);

      var wind = from;

      for (var elapsed = 0.0f; elapsed < SpeedSeconds; elapsed += Frame)
      {
        wind = WindSmoothing.Approach(wind, to, Frame, SpeedSeconds, BearingSeconds);
      }

      // One time constant is 1 - 1/e of the way: 4 to 20 m/s reaches about 14.1, and 0 to 90 degrees only 20.
      Assert.InRange(wind.Length(), 13.5f, 14.5f);
      Assert.InRange(Between(from, wind), 18.0f, 22.0f);
    }

    /// <summary>
    /// Sixty steps of a frame land where one step of a second does, in both halves.
    /// </summary>
    /// <remarks>
    /// True in the arithmetic, and to a part in a hundred thousand in float32. Worth pinning because it is not
    /// obvious: the bearing is re-measured on every call, but the angle still remaining decays by the same
    /// factor however the second is cut up. A plain lerp by <c>delta</c> is out by percent here, and the wind
    /// would settle somewhere different on a fast machine than on a slow one.
    /// </remarks>
    [Fact]
    public void TheResultDoesNotDependOnTheFramerate()
    {
      var from = Wind(5.0f, 20.0f);
      var to = Wind(18.0f, 140.0f);

      var stepped = from;

      for (var frame = 0; frame < 60; frame++)
      {
        stepped = WindSmoothing.Approach(stepped, to, Frame, SpeedSeconds, BearingSeconds);
      }

      var once = WindSmoothing.Approach(from, to, 1.0f, SpeedSeconds, BearingSeconds);

      Assert.True(
        (stepped - once).Length() < 0.001f,
        $"sixty frames reached {stepped}, one second reached {once}");
    }

    /// <summary>
    /// A wind of nothing takes the bearing it is handed, and one falling to nothing keeps its own.
    /// </summary>
    /// <remarks>
    /// Both are reachable rather than defensive. <c>_wind</c> starts at <c>Vector3.Zero</c> and stays there
    /// until the first message, and a debug speed of nought builds a zero target. Neither vector has a bearing
    /// to measure, and asking for one gives a turn of nothing rather than an error - so the guards have to be
    /// explicit, and a zero wind that kept "its own" bearing would never leave the origin.
    /// </remarks>
    [Fact]
    public void ADeadCalmHasNoBearingToKeep()
    {
      var target = Wind(10.0f, 45.0f);
      var raised = WindSmoothing.Approach(Vector3.Zero, target, 1.0f, SpeedSeconds, BearingSeconds);

      Assert.True(raised.Length() > 0.0f, "a wind rising out of a dead calm stayed at zero");
      Assert.True(
        raised.Normalized().IsEqualApprox(target.Normalized()),
        $"a wind rising out of a dead calm came up on {raised} rather than along {target}");

      var from = Wind(10.0f, 45.0f);
      var dying = WindSmoothing.Approach(from, Vector3.Zero, 1.0f, SpeedSeconds, BearingSeconds);

      Assert.True(dying.Length() < from.Length(), "a wind dropping to nothing did not slow");
      Assert.True(
        dying.Normalized().IsEqualApprox(from.Normalized()),
        $"a wind dropping to nothing turned to {dying} on the way");

      Assert.Equal(
        Vector3.Zero, WindSmoothing.Approach(Vector3.Zero, Vector3.Zero, 1.0f, SpeedSeconds, BearingSeconds));
    }
  }
}
