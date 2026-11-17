using Godot;

namespace BestiaBehemothClient.Game.UI.Quest
{
  /// <summary>Hands <see cref="QuestFormat"/> to GDScript, which cannot call a static C# method.</summary>
  [GlobalClass]
  public partial class QuestFormatter : RefCounted
  {
    public string LevelBand(int suggestedLevel, int masterLevel)
    {
      return QuestFormat.LevelBand(suggestedLevel, masterLevel);
    }

    public string TimeLeftUnit(long deadlineEpochMs, long nowEpochMs)
    {
      return QuestFormat.TimeLeftUnit(deadlineEpochMs, nowEpochMs);
    }

    public long TimeLeftAmount(long deadlineEpochMs, long nowEpochMs)
    {
      return QuestFormat.TimeLeftAmount(deadlineEpochMs, nowEpochMs);
    }

    public bool OfferFits(long levelsLow, long levelsHigh, int masterLevel)
    {
      return QuestFormat.OfferFits(levelsLow, levelsHigh, masterLevel);
    }

    public long BoardFee(long pay, int percent)
    {
      return QuestFormat.BoardFee(pay, percent);
    }
  }
}
