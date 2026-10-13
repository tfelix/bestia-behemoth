using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The footprint ring: capacity, aging, and how a server snapshot replaces what was laid locally.
  /// </summary>
  /// <remarks>
  /// Expectations are worked out by hand from contract section 2: a print of level L has <c>L/16</c> of its
  /// lifetime left, is drawn at <c>L * 255 / 16</c>, and the wire byte <c>b</c> means level <c>(b + 1) &gt;&gt; 4</c>.
  /// </remarks>
  public class ClientColumnStampsTest
  {
    /// <summary>Twenty minutes in milliseconds, the default: 3600 Bestia seconds at three times real time.</summary>
    private const long Lifetime = 1_200_000;

    private const long Now = 100_000;

    private static ClientColumnStamps Ring(int capacity = 48) => new(capacity, Lifetime);

    private static GroundStamp Wire(int cell, int octant, int seed, byte strength) =>
      new(cell, GroundStampCells.Footprint, octant, seed, strength);

    [Fact]
    public void AFreshPrintIsDrawnAtFullStrength()
    {
      var ring = Ring();

      Assert.True(ring.Add(5, 2, 9, Now));

      Assert.Equal(255, ring.StampAt(0, Now).Strength);
    }

    /// <summary>A print drops a level per sixteenth of its life, and is never drawn fainter than the last step.</summary>
    [Theory]
    [InlineData(0L, 255)]
    [InlineData(1L, 239)]
    [InlineData(75_000L, 239)]
    [InlineData(75_001L, 223)]
    [InlineData(600_000L, 127)]
    [InlineData(1_199_999L, 15)]
    public void StrengthFallsInSixteenthsOfItsLife(long ageMillis, int strength)
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);

      Assert.Equal(strength, ring.StampAt(0, Now + ageMillis).Strength);
    }

    [Fact]
    public void APrintPastItsLifetimeIsDroppedByTheNextTick()
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);

      Assert.False(ring.IsLive(0, Now + Lifetime));
      Assert.True(ring.Age(Now + Lifetime));
      Assert.True(ring.IsEmpty);
    }

    /// <summary>A tick that crosses no level step has nothing to redraw, which is what keeps the field still.</summary>
    [Fact]
    public void ATickBetweenLevelStepsChangesNothing()
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);

      // Level 16 is only the first millisecond: 1_199_999 ms left is already 15.
      Assert.True(ring.Age(Now + 1_000));
      Assert.False(ring.Age(Now + 2_000));
      Assert.False(ring.Age(Now + 74_999));
      Assert.True(ring.Age(Now + 75_001));
    }

    [Fact]
    public void TheOldestPrintGoesWhenTheRingIsFull()
    {
      var ring = Ring(capacity: 3);

      ring.Add(1, 0, 0, Now);
      ring.Add(2, 0, 0, Now);
      ring.Add(3, 0, 0, Now);
      ring.Add(4, 0, 0, Now);

      Assert.Equal(3, ring.Count);
      Assert.Equal(2, ring.StampAt(0, Now).CellIndex);
      Assert.Equal(4, ring.StampAt(2, Now).CellIndex);
    }

    [Fact]
    public void TheSameFootfallReportedTwiceInAMomentIsOnePrint()
    {
      var ring = Ring();

      Assert.True(ring.Add(5, 2, 9, Now));
      Assert.False(ring.Add(5, 2, 9, Now + 1_000));
      Assert.Equal(1, ring.Count);

      Assert.True(ring.Add(5, 2, 9, Now + 2_000));
      Assert.Equal(2, ring.Count);
    }

    [Fact]
    public void AnotherHeadingOrShapeOnTheSameTileIsAnotherPrint()
    {
      var ring = Ring();

      Assert.True(ring.Add(5, 2, 9, Now));
      Assert.True(ring.Add(5, 3, 9, Now));
      Assert.True(ring.Add(5, 2, 10, Now));
    }

    /// <summary>A print for a step the server never took is a phantom; the snapshot has to heal it.</summary>
    [Fact]
    public void ASnapshotDropsALocalPrintItDoesNotListOnceItIsOldEnough()
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);

      var merge = ring.ApplySnapshot(new[] { Wire(7, 1, 3, 255) }, Now + 10_000);

      Assert.Equal(1, ring.Count);
      Assert.Equal(7, ring.StampAt(0, Now + 10_000).CellIndex);
      Assert.Equal(1, merge.Prior);
      Assert.Equal(1, merge.LocalDropped);
      Assert.Equal(1, merge.SnapshotNew);
    }

    /// <summary>The server cannot have seen a print laid a moment ago, so its silence about it means nothing yet.</summary>
    [Fact]
    public void ASnapshotKeepsALocalPrintYoungEnoughToBeInFlight()
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);

      var merge = ring.ApplySnapshot(new[] { Wire(7, 1, 3, 255) }, Now + 1_000);

      Assert.Equal(2, ring.Count);
      Assert.Equal(0, merge.LocalDropped);

      // The survivor draws on top of what the server listed.
      Assert.Equal(7, ring.StampAt(0, Now + 1_000).CellIndex);
      Assert.Equal(5, ring.StampAt(1, Now + 1_000).CellIndex);
    }

    [Fact]
    public void APrintTheSnapshotListsIsTheSameOneRatherThanAnAddition()
    {
      var ring = Ring();
      ring.Add(7, 1, 3, Now);

      var merge = ring.ApplySnapshot(new[] { Wire(7, 1, 3, 127) }, Now + 500);

      Assert.Equal(1, ring.Count);
      Assert.Equal(0, merge.LocalDropped);
      Assert.Equal(0, merge.SnapshotNew);
    }

    /// <summary>The server knows how long it has really been, and its strength byte is how it says so.</summary>
    [Fact]
    public void ASnapshotPrintTakesItsAgeFromTheStrengthTheServerSent()
    {
      var ring = Ring();

      // 127 is level 8: half the life left, so half of it already gone.
      ring.ApplySnapshot(new[] { Wire(7, 1, 3, 127), Wire(8, 1, 3, 255), Wire(9, 1, 3, 15) }, Now);

      Assert.Equal(127, ring.StampAt(0, Now).Strength);
      Assert.Equal(255, ring.StampAt(1, Now).Strength);
      Assert.Equal(15, ring.StampAt(2, Now).Strength);
    }

    /// <summary>239 is level 15, a sixteenth gone: 75 s of the 20 minutes. Another 75 s on, it is level 14.</summary>
    [Fact]
    public void ASnapshotPrintKeepsAgeingFromWhereTheServerLeftIt()
    {
      var ring = Ring();

      ring.ApplySnapshot(new[] { Wire(7, 1, 3, 239) }, Now);

      Assert.Equal(239, ring.StampAt(0, Now).Strength);
      Assert.Equal(223, ring.StampAt(0, Now + 75_000).Strength);
    }

    [Fact]
    public void TwoLocalPrintsAreClaimedOneToOneByTwoSnapshotPrints()
    {
      var ring = Ring();
      ring.Add(7, 1, 3, Now - 20_000);
      ring.Add(7, 1, 3, Now - 10_000);

      var merge = ring.ApplySnapshot(new[] { Wire(7, 1, 3, 255), Wire(7, 1, 3, 255) }, Now);

      Assert.Equal(2, ring.Count);
      Assert.Equal(0, merge.LocalDropped);
      Assert.Equal(0, merge.SnapshotNew);
    }

    [Fact]
    public void AnEmptySnapshotRetiresEverythingOldEnough()
    {
      var ring = Ring();
      ring.Add(5, 2, 9, Now);
      ring.Add(6, 2, 9, Now + 9_500);

      ring.ApplySnapshot(new GroundStamp[0], Now + 10_000);

      Assert.Equal(1, ring.Count);
      Assert.Equal(6, ring.StampAt(0, Now + 10_000).CellIndex);
    }

    [Fact]
    public void ASnapshotLongerThanTheRingKeepsTheNewestPrints()
    {
      var ring = Ring(capacity: 2);

      ring.ApplySnapshot(new[] { Wire(1, 0, 0, 255), Wire(2, 0, 0, 255), Wire(3, 0, 0, 255) }, Now);

      Assert.Equal(2, ring.Count);
      Assert.Equal(2, ring.StampAt(0, Now).CellIndex);
      Assert.Equal(3, ring.StampAt(1, Now).CellIndex);
    }
  }
}
