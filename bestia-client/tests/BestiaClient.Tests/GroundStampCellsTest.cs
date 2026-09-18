using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client half of the ground-stamp wire contract.
  /// </summary>
  /// <remarks>
  /// Written from hand-typed bytes rather than from anything the server produces, which is the only way this
  /// catches a disagreement: the failure mode is not an exception but tracks drawn pointing the wrong way, and
  /// nobody files that as a bug. <c>ColumnStampsTest</c> pins the same bytes on the server side.
  /// </remarks>
  public class GroundStampCellsTest
  {
    private const int ChunkSize = 32;

    /// <summary>The fixture <c>ColumnStampsTest</c> encodes, byte for byte.</summary>
    private static readonly byte[] TwoStamps =
    {
      0x00, 0x00, 0x10, 0x01, 0x7F,
      0xA3, 0x00, 0x16, 0xC8, 0xFF,
    };

    [Fact]
    public void FiveBytesMakeOneStamp()
    {
      Assert.Equal(2, GroundStampCells.CountIn(TwoStamps));
      Assert.Equal(0, GroundStampCells.CountIn(null));
    }

    [Fact]
    public void AStampUnpacksToWhatTheServerPackedIn()
    {
      var stamp = GroundStampCells.At(TwoStamps, 1);

      Assert.Equal(5 * ChunkSize + 3, stamp.CellIndex);
      Assert.Equal(GroundStampCells.Footprint, stamp.Kind);
      Assert.Equal(6, stamp.Octant);
      Assert.Equal(200, stamp.Seed);
      Assert.Equal(255, stamp.Strength);
    }

    [Fact]
    public void CellsRunAlongXBeforeY()
    {
      var stamp = GroundStampCells.At(TwoStamps, 1);

      Assert.Equal(3, stamp.LocalX(ChunkSize));
      Assert.Equal(5, stamp.LocalY(ChunkSize));
    }

    /// <summary>The kind and the heading share a byte, and reading either one wrong is silent.</summary>
    [Fact]
    public void TheKindIsTheHighNibbleAndTheHeadingTheLowBits()
    {
      var straightOn = GroundStampCells.At(TwoStamps, 0);

      Assert.Equal(GroundStampCells.Footprint, straightOn.Kind);
      Assert.Equal(0, straightOn.Octant);
      Assert.Equal(1, straightOn.Seed);
    }

    /// <summary>Oldest first, so the newest print draws on top of the ones it overlaps.</summary>
    [Fact]
    public void StampsDecodeInTheOrderTheyWereLaid()
    {
      var stamps = GroundStampCells.Decode(TwoStamps);

      Assert.Equal(2, stamps.Length);
      Assert.True(stamps[0].Strength < stamps[1].Strength, "the fresher print did not come last");
    }

    [Fact]
    public void AnEmptyPayloadIsAColumnNothingHasCrossed()
    {
      Assert.Empty(GroundStampCells.Decode(new byte[0]));
    }
  }
}
