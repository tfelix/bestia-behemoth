using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The moon's phase: one lunar cycle per Bestia month.
  /// </summary>
  /// <remarks>
  /// Client-only and purely visual, so it is derived from the clock rather than sent. The month is the cycle
  /// because a calendar month already reads as "one moon" to a player.
  ///
  /// <para>
  /// Phase 0 is new moon, 0.5 full moon. The moon trails the sun across the sky by the same fraction of a day,
  /// so a new moon stands beside the sun and is lost in daylight, and a full moon rises as the sun sets.
  /// </para>
  /// </remarks>
  public readonly struct MoonCycle
  {
    private const double SecondsPerHour = 3600.0;

    public MoonCycle(int hoursPerDay, int daysPerMonth)
    {
      HoursPerDay = hoursPerDay;
      DaysPerMonth = daysPerMonth;
    }

    public int HoursPerDay { get; }
    public int DaysPerMonth { get; }

    public bool IsValid => HoursPerDay > 0 && DaysPerMonth > 0;

    /// <summary>How far through its cycle the moon is at a world age, in <c>[0, 1)</c>.</summary>
    public double PhaseAt(double bestiaSeconds)
    {
      if (!IsValid)
      {
        return 0.5;
      }

      var secondsPerCycle = DaysPerMonth * HoursPerDay * SecondsPerHour;
      var phase = bestiaSeconds / secondsPerCycle;

      return phase - Math.Floor(phase);
    }

    /// <summary>How much of the disc is lit, in <c>[0, 1]</c>: 0 at new moon, 1 at full moon.</summary>
    public static double IlluminationOf(double phase) => (1.0 - Math.Cos(2.0 * Math.PI * phase)) / 2.0;
  }
}
