namespace BestiaBehemothClient.Game.UI.Quest
{
  /// <summary>
  /// How the quest windows rate and time a quest. Level bands follow the colours players know from other games:
  /// trivial (grey), easy (green), fair (yellow), hard (orange) and deadly (red).
  /// </summary>
  public static class QuestFormat
  {
    public static string LevelBand(int suggestedLevel, int masterLevel)
    {
      var lead = suggestedLevel - masterLevel;
      if (lead <= -10) return "trivial";
      if (lead <= -3) return "easy";
      if (lead <= 2) return "fair";
      if (lead <= 5) return "hard";

      return "deadly";
    }

    /// <summary>The largest whole unit left: <c>days</c>, <c>hours</c> or <c>minutes</c>, or <c>ended</c>.</summary>
    public static string TimeLeftUnit(long deadlineEpochMs, long nowEpochMs)
    {
      var minutes = MinutesLeft(deadlineEpochMs, nowEpochMs);
      if (minutes <= 0) return "ended";
      if (minutes >= MinutesPerDay) return "days";
      if (minutes >= 60) return "hours";

      return "minutes";
    }

    public static long TimeLeftAmount(long deadlineEpochMs, long nowEpochMs)
    {
      var minutes = MinutesLeft(deadlineEpochMs, nowEpochMs);
      return TimeLeftUnit(deadlineEpochMs, nowEpochMs) switch
      {
        "days" => minutes / MinutesPerDay,
        "hours" => minutes / 60,
        "minutes" => minutes,
        _ => 0
      };
    }

    /// <summary>
    /// Whether a board's open levels hold one worth the master's time: not trivial and not deadly. Bit n of
    /// <paramref name="levelsLow"/> is level n + 1, bit n of <paramref name="levelsHigh"/> level n + 65.
    /// </summary>
    public static bool OfferFits(long levelsLow, long levelsHigh, int masterLevel)
    {
      for (var level = 1; level <= 128; level++)
      {
        var mask = level <= 64 ? levelsLow : levelsHigh;
        var bit = (level - 1) % 64;
        if ((mask >> bit & 1L) == 0) continue;

        var band = LevelBand(level, masterLevel);
        if (band != "trivial" && band != "deadly") return true;
      }

      return false;
    }

    /// <summary>The board's fee: the server's <c>BlackboardFees</c>, rounded up, at least one coin.</summary>
    public static long BoardFee(long pay, int percent)
    {
      return System.Math.Max(1L, (pay * percent + 99) / 100);
    }

    private const long MinutesPerDay = 24 * 60;

    // Rounded up, so a quest with seconds to go still shows a minute rather than "ended".
    private static long MinutesLeft(long deadlineEpochMs, long nowEpochMs)
    {
      var left = deadlineEpochMs - nowEpochMs;
      return left <= 0 ? 0 : (left + 59_999) / 60_000;
    }
  }
}
