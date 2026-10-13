using System.IO;
using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client half of the ground-layer wire contract.
  /// </summary>
  /// <remarks>
  /// Written from hand-typed bytes rather than from anything the server produces, which is the only way this
  /// catches a disagreement: the failure mode is not an exception but plausible wear drawn in the wrong
  /// places, and nobody files that as a bug. <c>ColumnLevelsTest</c> and <c>GroundLayerTest</c> pin the same
  /// bytes and the same numbers on the server side.
  /// </remarks>
  public class GroundLayerCellsTest
  {
    private const int ChunkSize = 32;

    [Fact]
    public void TwoCellsShareAByteLowNibbleFirst()
    {
      // The byte ColumnLevelsTest builds on the server: cell 0 at level 1, cell 1 at level 15.
      var cells = new byte[GroundLayerCells.ByteLength(ChunkSize)];
      cells[0] = 0xF1;

      Assert.Equal(1, GroundLayerCells.LevelAt(cells, ChunkSize, 0, 0));
      Assert.Equal(15, GroundLayerCells.LevelAt(cells, ChunkSize, 1, 0));
    }

    [Fact]
    public void CellsRunAlongXBeforeY()
    {
      var cells = new byte[GroundLayerCells.ByteLength(ChunkSize)];

      // Cell (0, 1) is index 32, so byte 16's low nibble.
      cells[16] = 0x07;

      Assert.Equal(7, GroundLayerCells.LevelAt(cells, ChunkSize, 0, 1));
      Assert.Equal(0, GroundLayerCells.LevelAt(cells, ChunkSize, 1, 0));
    }

    [Fact]
    public void AColumnIsHalfAByteACell()
    {
      Assert.Equal(ChunkSize * ChunkSize / 2, GroundLayerCells.ByteLength(ChunkSize));
    }

    [Fact]
    public void AnAbsentLayerReadsAsUnmarkedRatherThanThrowing()
    {
      Assert.Equal(0, GroundLayerCells.LevelAt(null, ChunkSize, 5, 5));
    }

    [Fact]
    public void TheStrongestLevelIsEveryBitOfTheNibble()
    {
      var cells = new byte[GroundLayerCells.ByteLength(ChunkSize)];
      cells[0] = 0x0F;

      Assert.Equal(GroundLayerCells.MaxLevel, GroundLayerCells.LevelAt(cells, ChunkSize, 0, 0));
    }

    [Fact]
    public void ChannelsAreWhatTheShaderCompositesBy()
    {
      Assert.Equal(0, GroundLayers.ChannelOf(GroundLayers.Id.Scorched));
      Assert.Equal(1, GroundLayers.ChannelOf(GroundLayers.Id.Worn));
      Assert.Equal(2, GroundLayers.ChannelOf(GroundLayers.Id.Bloodied));
      Assert.Equal(3, GroundLayers.ChannelOf(GroundLayers.Id.Disturbed));
    }

    [Fact]
    public void WireIdsAreWhatTheServerSends()
    {
      Assert.Equal(1, (int)GroundLayers.Id.Scorched);
      Assert.Equal(2, (int)GroundLayers.Id.Worn);
      Assert.Equal(3, (int)GroundLayers.Id.Bloodied);
      Assert.Equal(4, (int)GroundLayers.Id.Disturbed);
    }

    [Fact]
    public void AnUnknownLayerIsSkippedRatherThanDrawnInSomebodyElsesChannel()
    {
      Assert.Equal(-1, GroundLayers.ChannelOf(GroundLayers.Id.Unspecified));
      Assert.Equal(-1, GroundLayers.ChannelOf((GroundLayers.Id)99));
    }

    /// <summary>The fixture the server's encoding test builds: cells 0, 33 and 1023 at levels 1, 255 and 16.</summary>
    private static readonly byte[] ThreeCells =
    {
      0x00, 0x00, 0x01,
      0x21, 0x00, 0xFF,
      0xFF, 0x03, 0x10,
    };

    [Fact]
    public void SparseCellsAreAnIndexLittleEndianAndALevel()
    {
      var levels = GroundLayerCells.DecodeSparse(ThreeCells, ChunkSize);

      Assert.Equal(ChunkSize * ChunkSize, levels.Length);
      Assert.Equal(1, levels[0]);
      Assert.Equal(255, levels[33]);
      Assert.Equal(16, levels[1023]);
    }

    [Fact]
    public void SparseCellsThePayloadDoesNotNameAreZero()
    {
      var levels = GroundLayerCells.DecodeSparse(ThreeCells, ChunkSize);

      Assert.Equal(3, levels.Count(level => level != 0));
    }

    [Fact]
    public void ASparsePayloadThatIsNotWholeCellsIsRefused()
    {
      Assert.Throws<InvalidDataException>(() => GroundLayerCells.DecodeSparse(new byte[] { 0, 0, 1, 33 }, ChunkSize));
    }

    [Fact]
    public void ASparseCellOutsideTheColumnIsRefused()
    {
      // Index 1024 in a column of 1024 cells.
      Assert.Throws<InvalidDataException>(
        () => GroundLayerCells.DecodeSparse(new byte[] { 0x00, 0x04, 0x01 }, ChunkSize));
    }

    [Fact]
    public void AnEntryWithNoCellsRetiresItsLayerWhateverTheEncoding()
    {
      var entry = GroundLayerCells.ReadEntry(GroundLayers.Id.Worn, 0, new byte[0], 0, ChunkSize);

      Assert.True(entry.Retires);
      Assert.Equal(GroundLayers.Id.Worn, entry.Layer);
    }

    [Fact]
    public void AGradedLayerReadsItsExactLevelsAndTheDecayClock()
    {
      var entry = GroundLayerCells.ReadEntry(
        GroundLayers.Id.Bloodied, GroundLayerCells.LevelsSparseV1, ThreeCells, 123_456, ChunkSize);

      Assert.False(entry.Retires);
      Assert.Equal(255, entry.Levels[33]);
      Assert.Equal(123_456, entry.LastDecayedSecond);
      Assert.Null(entry.Nibbles);
    }

    [Fact]
    public void ScorchIsDrawnAsSentFromItsNibbles()
    {
      var nibbles = new byte[GroundLayerCells.ByteLength(ChunkSize)];
      nibbles[0] = 0xF1;

      var entry = GroundLayerCells.ReadEntry(
        GroundLayers.Id.Scorched, GroundLayerCells.NibbleV1, nibbles, 0, ChunkSize);

      Assert.Same(nibbles, entry.Nibbles);
      Assert.Null(entry.Levels);
    }

    /// <summary>A nibble copy would seed wear that crosses the threshold at the wrong footfall.</summary>
    [Theory]
    [InlineData(GroundLayers.Id.Worn, GroundLayerCells.NibbleV1)]
    [InlineData(GroundLayers.Id.Bloodied, GroundLayerCells.NibbleV1)]
    [InlineData(GroundLayers.Id.Scorched, GroundLayerCells.LevelsSparseV1)]
    [InlineData(GroundLayers.Id.Worn, 9u)]
    public void AnEncodingThatDoesNotSuitItsLayerIsRefused(GroundLayers.Id layer, uint encoding)
    {
      var cells = new byte[GroundLayerCells.ByteLength(ChunkSize)];
      cells[0] = 1;

      Assert.Throws<InvalidDataException>(() => GroundLayerCells.ReadEntry(layer, encoding, cells, 0, ChunkSize));
    }

    [Fact]
    public void NibblesOfTheWrongLengthAreRefused()
    {
      Assert.Throws<InvalidDataException>(
        () => GroundLayerCells.ReadEntry(GroundLayers.Id.Scorched, GroundLayerCells.NibbleV1, new byte[10], 0, ChunkSize));
    }

    [Fact]
    public void EveryLayerFitsTheChannelsOneMarkTextureHas()
    {
      foreach (GroundLayers.Id layer in System.Enum.GetValues(typeof(GroundLayers.Id)))
      {
        var channel = GroundLayers.ChannelOf(layer);
        Assert.True(channel < GroundLayers.Channels, $"{layer} has no channel to live in");
      }
    }
  }
}
