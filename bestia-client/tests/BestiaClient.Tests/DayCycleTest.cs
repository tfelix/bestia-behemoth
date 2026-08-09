using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client's copy of the day's light curve, pinned against the server's.
  /// </summary>
  /// <remarks>
  /// The counterpart to zone-server's <c>BestiaDateTimeTest</c>, and the two are a pair on purpose. Light
  /// level is a per-frame quantity while the clock is an anchor sent once per connection, so this curve
  /// cannot be sent - it has to be evaluated on both sides, from boundaries that are. That makes "the two
  /// implementations agree" a real risk rather than a theoretical one, and the numbers below are the
  /// server's own assertions written out again.
  ///
  /// <para>
  /// A disagreement here does not throw or log. It ships as a client whose sunset is twenty minutes off the
  /// hour a nocturnal creature wakes up at, which nobody would report as a bug in a message format.
  /// </para>
  /// </remarks>
  public class DayCycleTest
  {
    /// <summary>The shipped calendar: 24 hours, dark 22:00-04:00, twilight two hours either side.</summary>
    private static readonly DayCycle Shipped = new(hoursPerDay: 24, nightEndHour: 4, dawnEndHour: 6,
      duskStartHour: 20, nightStartHour: 22);

    [Fact]
    public void FullNightStraddlesMidnight()
    {
      Assert.True(Shipped.IsNightAt(0.0));
      Assert.True(Shipped.IsNightAt(23.0));
      Assert.True(Shipped.IsNightAt(22.0));
      Assert.True(Shipped.IsNightAt(3.999));

      Assert.False(Shipped.IsNightAt(4.0));
      Assert.False(Shipped.IsNightAt(21.0));
      Assert.False(Shipped.IsNightAt(12.0));
    }

    /// <summary>
    /// Six dark hours, the length the docs give. Twilight sits on top of the dark rather than eating into
    /// it, so moving where night falls must not have quietly shortened it.
    /// </summary>
    [Fact]
    public void NightIsSixHoursLong()
    {
      var dark = Enumerable.Range(0, 24).Count(hour => Shipped.IsNightAt(hour));

      Assert.Equal(6, dark);
    }

    [Fact]
    public void DaylightIsFullByDayAndZeroAtNight()
    {
      Assert.Equal(1.0, Shipped.DaylightAt(12.0));
      Assert.Equal(1.0, Shipped.DaylightAt(6.0));
      Assert.Equal(1.0, Shipped.DaylightAt(19.999));

      Assert.Equal(0.0, Shipped.DaylightAt(0.0));
      Assert.Equal(0.0, Shipped.DaylightAt(22.0));
      Assert.Equal(0.0, Shipped.DaylightAt(3.999));
    }

    /// <summary>
    /// The ramps hit halfway at their midpoints, which is where the sun crosses the horizon - and where
    /// <see cref="DayCycle.SunriseHour"/> and <see cref="DayCycle.SunsetHour"/> put it.
    /// </summary>
    [Fact]
    public void TheRampsAreHalfLitWhereTheSunCrossesTheHorizon()
    {
      Assert.Equal(5.0, Shipped.SunriseHour);
      Assert.Equal(21.0, Shipped.SunsetHour);

      Assert.Equal(0.5, Shipped.DaylightAt(Shipped.SunriseHour), 9);
      Assert.Equal(0.5, Shipped.DaylightAt(Shipped.SunsetHour), 9);
    }

    /// <summary>
    /// A sky that brightens then dims again inside one dawn reads as a bug however smooth each half is.
    /// </summary>
    [Fact]
    public void TheRampsAreMonotone()
    {
      var dawn = Enumerable.Range(0, 121).Select(i => Shipped.DaylightAt(4.0 + i / 60.0)).ToList();
      Assert.Equal(dawn.OrderBy(v => v), dawn);

      var dusk = Enumerable.Range(0, 121).Select(i => Shipped.DaylightAt(20.0 + i / 60.0)).ToList();
      Assert.Equal(dusk.OrderByDescending(v => v), dusk);
    }

    /// <summary>
    /// Night is always fully dark, so a creature the server put to sleep is never asleep in a lit world.
    ///
    /// <para>
    /// An implication rather than an equality, matching the server's own test: at 04:00:00 sharp full night
    /// has ended and the dawn ramp has not yet risen off zero, and both readings are right there.
    /// </para>
    /// </summary>
    [Fact]
    public void NightIsAlwaysFullyDark()
    {
      for (var minute = 0; minute < 24 * 60; minute++)
      {
        var hour = minute / 60.0;

        if (Shipped.IsNightAt(hour))
        {
          Assert.Equal(0.0, Shipped.DaylightAt(hour));
        }
      }
    }

    /// <summary>
    /// An unset or contradictory cycle reads as full day, never as night.
    /// </summary>
    /// <remarks>
    /// The direction is the point. This is what a client has before the world info arrives, and black would
    /// mean every login flashing a night at the player and the Game scene opening unlit in the editor.
    /// </remarks>
    [Fact]
    public void AnUnusableCycleStaysLit()
    {
      Assert.False(default(DayCycle).IsValid);
      Assert.Equal(1.0, default(DayCycle).DaylightAt(2.0));
      Assert.False(default(DayCycle).IsNightAt(2.0));

      // Boundaries out of order - the shape the client would see from a server that reordered them.
      var scrambled = new DayCycle(24, nightEndHour: 20, dawnEndHour: 6, duskStartHour: 4, nightStartHour: 22);

      Assert.False(scrambled.IsValid);
      Assert.Equal(1.0, scrambled.DaylightAt(23.0));
    }

    /// <summary>Hours outside a single day wrap rather than clamping, so a raw clock reading is safe to pass.</summary>
    [Fact]
    public void HoursWrapIntoTheDay()
    {
      Assert.Equal(Shipped.DaylightAt(2.0), Shipped.DaylightAt(26.0));
      Assert.Equal(Shipped.DaylightAt(13.0), Shipped.DaylightAt(24.0 * 7 + 13.0));
      Assert.True(Shipped.IsNightAt(-1.0));
    }
  }
}
