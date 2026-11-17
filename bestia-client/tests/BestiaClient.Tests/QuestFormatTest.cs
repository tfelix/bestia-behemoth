using BestiaBehemothClient.Game.UI.Quest;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class QuestFormatTest
  {
    [Theory]
    [InlineData(10, 20, "trivial")]
    [InlineData(17, 20, "easy")]
    [InlineData(20, 20, "fair")]
    [InlineData(22, 20, "fair")]
    [InlineData(25, 20, "hard")]
    [InlineData(26, 20, "deadly")]
    public void RatesTheSuggestedLevelAgainstTheMaster(int suggested, int master, string band)
    {
      Assert.Equal(band, QuestFormat.LevelBand(suggested, master));
    }

    [Theory]
    [InlineData(3 * Day + 5 * Hour, "days", 3)]
    [InlineData(6 * Hour + 30 * Minute, "hours", 6)]
    [InlineData(45 * Minute, "minutes", 45)]
    [InlineData(10_000, "minutes", 1)]
    [InlineData(0, "ended", 0)]
    [InlineData(-Hour, "ended", 0)]
    public void ShowsTheLargestWholeUnitLeft(long left, string unit, long amount)
    {
      const long now = 1_800_000_000_000;

      Assert.Equal(unit, QuestFormat.TimeLeftUnit(now + left, now));
      Assert.Equal(amount, QuestFormat.TimeLeftAmount(now + left, now));
    }

    [Theory]
    [InlineData(480, 5, 24)]
    [InlineData(101, 3, 4)]
    [InlineData(2, 3, 1)]
    public void PricesTheBoardFeeLikeTheServer(long pay, int percent, long fee)
    {
      Assert.Equal(fee, QuestFormat.BoardFee(pay, percent));
    }

    private const long Minute = 60_000;
    private const long Hour = 60 * Minute;
    private const long Day = 24 * Hour;
  }
}
