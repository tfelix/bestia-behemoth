using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Where the light is at a given hour: the shape of one Bestia day, as the server stated it.
  /// </summary>
  /// <remarks>
  /// <code>
  /// 00 ---- NightEnd -- DawnEnd ------------ DuskStart -- NightStart ---- 24
  ///   full night   |  dawn  |    full day      |   dusk   |  full night
  /// </code>
  ///
  /// <para>
  /// A plain value type with no engine in it, deliberately - the same reason <c>BlockAppearance</c> takes
  /// palette entries rather than the message they arrived on. This is the client's copy of a curve the server
  /// also evaluates, so the two agreeing has to be something a test can check, and a test cannot construct a
  /// <see cref="Node"/>. <see cref="WorldClock"/> holds one of these and adds the clock.
  /// </para>
  ///
  /// <para>
  /// Re-implemented on the client rather than sent, because light level is a per-frame quantity while the
  /// clock is an anchor delivered once per connection. The <i>boundaries</i> are what travels, so the two
  /// implementations agree by sharing their inputs rather than by both being remembered.
  /// </para>
  /// </remarks>
  public readonly struct DayCycle
  {
    public DayCycle(int hoursPerDay, int nightEndHour, int dawnEndHour, int duskStartHour, int nightStartHour)
    {
      HoursPerDay = hoursPerDay;
      NightEndHour = nightEndHour;
      DawnEndHour = dawnEndHour;
      DuskStartHour = duskStartHour;
      NightStartHour = nightStartHour;
    }

    public int HoursPerDay { get; }
    public int NightEndHour { get; }
    public int DawnEndHour { get; }
    public int DuskStartHour { get; }
    public int NightStartHour { get; }

    /// <summary>
    /// Whether the boundaries are a day this can actually resolve.
    /// </summary>
    /// <remarks>
    /// The ordering is what lets <see cref="DaylightAt"/> be a single descending chain of comparisons with no
    /// wrap-around case - only full night wraps midnight, and it wraps because it is the two open ends of
    /// that ordering. A default-constructed cycle is all zeroes and so fails this, which is how "the server
    /// has not said yet" is represented without a second flag.
    /// </remarks>
    public bool IsValid =>
      NightEndHour < DawnEndHour &&
      DawnEndHour < DuskStartHour &&
      DuskStartHour < NightStartHour &&
      NightStartHour < HoursPerDay;

    /// <summary>The hour the sun crosses the horizon rising: the middle of the dawn ramp, where light is half up.</summary>
    public double SunriseHour => (NightEndHour + DawnEndHour) / 2.0;

    /// <summary>The hour the sun crosses the horizon setting: the middle of the dusk ramp.</summary>
    public double SunsetHour => (DuskStartHour + NightStartHour) / 2.0;

    /// <summary>
    /// How much of the sun's light is up at <paramref name="hourOfDay"/>, in <c>[0, 1]</c>.
    /// </summary>
    /// <remarks>
    /// Smoothstepped rather than linear, matching the server: a linear ramp has a corner at each end, and a
    /// corner in a light level is visible as a moment where the sky stops changing - which reads as a hitch
    /// rather than as dusk ending.
    ///
    /// <para>
    /// Full day for a cycle that is not <see cref="IsValid"/>. That covers the window before the world info
    /// arrives, and the direction matters: black would mean a login flashing a night at the player and an
    /// unlit Game scene in the editor.
    /// </para>
    /// </remarks>
    public double DaylightAt(double hourOfDay)
    {
      if (!IsValid)
      {
        return 1.0;
      }

      var h = Mathf.PosMod(hourOfDay, HoursPerDay);

      if (h < NightEndHour)
      {
        return 0.0;
      }

      if (h < DawnEndHour)
      {
        return Smoothstep((h - NightEndHour) / (DawnEndHour - NightEndHour));
      }

      if (h < DuskStartHour)
      {
        return 1.0;
      }

      if (h < NightStartHour)
      {
        return 1.0 - Smoothstep((h - DuskStartHour) / (NightStartHour - DuskStartHour));
      }

      return 0.0;
    }

    /// <summary>
    /// Whether <paramref name="hourOfDay"/> falls in full night - the dark middle, not merely short of noon.
    /// </summary>
    public bool IsNightAt(double hourOfDay)
    {
      if (!IsValid)
      {
        return false;
      }

      var h = Mathf.PosMod(hourOfDay, HoursPerDay);

      return h >= NightStartHour || h < NightEndHour;
    }

    /// <summary>Hermite ease over <c>[0, 1]</c>, clamped. The ramp shape the server's curve uses.</summary>
    private static double Smoothstep(double t)
    {
      var x = Mathf.Clamp(t, 0.0, 1.0);

      return x * x * (3.0 - 2.0 * x);
    }
  }
}
