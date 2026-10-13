using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client's byte-resolution copy of a graded layer, which has to fade exactly as the server's does.
  /// </summary>
  /// <remarks>
  /// The fade fixtures are contract section 3's literals; <c>MarkedColumnTest</c> pins the same sequence on the
  /// server. Written down by hand because the remainder rule is the part that cannot fail loudly: a client that
  /// rounded the clock up would fade a little slow and nobody would see why.
  /// </remarks>
  public class ClientColumnLevelsTest
  {
    private const int Size = 32;

    /// <summary>Three Bestia days, the worn layer's default fade.</summary>
    private const int Fade = 259_200;

    private static ClientColumnLevels Full(long last = 0, int threshold = 32)
    {
      var levels = new ClientColumnLevels(Size, Fade, threshold, last);

      for (var i = 0; i < Size * Size; i++)
      {
        levels.Add(i, 255);
      }

      return levels;
    }

    [Fact]
    public void LessThanOneStepOfTimeChangesNothingAndKeepsTheClock()
    {
      var levels = Full();

      Assert.False(levels.AgeTo(180));

      Assert.Equal(0, levels.LastDecayedSecond);
      Assert.Equal(255, levels[0]);
    }

    [Fact]
    public void HalfTheFadeGivesHalfTheLevel()
    {
      var levels = Full();

      Assert.True(levels.AgeTo(129_600));

      // 129600 * 255 / 259200 is 127.5, so 127 steps; the clock advances by only what those paid for.
      Assert.Equal(129_091, levels.LastDecayedSecond);
      Assert.Equal(128, levels[0]);
      Assert.Equal(128, levels[Size * Size - 1]);
    }

    [Fact]
    public void TheRemainderIsKeptRatherThanDiscardedEveryPass()
    {
      var levels = Full();

      levels.AgeTo(1_017);
      Assert.Equal(1_016, levels.LastDecayedSecond);
      Assert.Equal(254, levels[0]);

      levels.AgeTo(2_032);
      Assert.Equal(1_016, levels.LastDecayedSecond);
      Assert.Equal(254, levels[0]);

      levels.AgeTo(2_033);
      Assert.Equal(2_032, levels.LastDecayedSecond);
      Assert.Equal(253, levels[0]);
    }

    [Fact]
    public void ACellFadesToNothingAndStaysThere()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);
      levels.Add(7, 3);

      levels.AgeTo(10L * Fade);

      Assert.Equal(0, levels[7]);
      Assert.True(levels.IsEmpty);
    }

    [Fact]
    public void AddingSaturatesAtTheTopOfTheByte()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);

      levels.Add(0, 200);
      levels.Add(0, 100);

      Assert.Equal(255, levels[0]);
    }

    /// <summary>One footfall is worth far less than a visible step, so a cell must collect several before it shows.</summary>
    [Fact]
    public void ACellIsNotDrawnUntilItReachesTheThreshold()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);

      Assert.False(levels.Add(0, 31));
      Assert.Equal(0, levels.DisplayAt(0));

      // 32 is the threshold, and the top four bits of 32 are 2.
      Assert.True(levels.Add(0, 1));
      Assert.Equal(2, levels.DisplayAt(0));

      // 48 moves the drawn level on; 49 does not.
      Assert.True(levels.Add(0, 16));
      Assert.Equal(3, levels.DisplayAt(0));
      Assert.False(levels.Add(0, 1));
    }

    [Fact]
    public void TheDrawnLevelsPackTwoCellsToAByteLowNibbleFirst()
    {
      var levels = new ClientColumnLevels(Size, Fade, 16, 0);

      levels.Add(0, 255);
      levels.Add(1, 16);
      levels.Add(33, 100);
      levels.Add(2, 15);

      var nibbles = levels.ToNibbles();

      // Cell 0 is level 15 and cell 1 is level 1, so byte 0 is 0x1F; cell 33 is level 6, in byte 16's high nibble.
      Assert.Equal(GroundLayerCells.ByteLength(Size), nibbles.Length);
      Assert.Equal(0x1F, nibbles[0]);
      Assert.Equal(0x60, nibbles[16]);

      // Cell 2 is below the threshold and so is not drawn at all.
      Assert.Equal(0, nibbles[1]);
    }

    [Fact]
    public void NothingVisibleIsNoCellsAtAll()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);
      levels.Add(0, 31);

      Assert.Null(levels.ToNibbles());
    }

    [Fact]
    public void ASnapshotReplacesEveryCellAndAdoptsTheServersClock()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);
      levels.Add(0, 100);
      levels.Add(1, 50);
      levels.Add(2, 10);

      var snapshot = new byte[Size * Size];
      snapshot[0] = 101;
      snapshot[1] = 60;
      snapshot[3] = 7;

      var merge = levels.Replace(snapshot, 5_000);

      Assert.Equal(101, levels[0]);
      Assert.Equal(60, levels[1]);
      Assert.Equal(0, levels[2]);
      Assert.Equal(7, levels[3]);
      Assert.Equal(5_000, levels.LastDecayedSecond);

      // Cell 0 is one level off, which is jitter; cells 1, 2 and 3 are 10, 10 and 7 off.
      Assert.Equal(3, merge.Prior);
      Assert.Equal(3, merge.Drifted);
    }

    [Fact]
    public void ASnapshotOfNothingEmptiesTheColumn()
    {
      var levels = new ClientColumnLevels(Size, Fade, 32, 0);
      levels.Add(0, 100);

      levels.Replace(new byte[Size * Size], 0);

      Assert.True(levels.IsEmpty);
    }
  }
}
