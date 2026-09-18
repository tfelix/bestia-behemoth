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
      Assert.Equal(0f, GroundLayerCells.UnitAt(null, ChunkSize, 5, 5));
    }

    [Fact]
    public void TheStrongestLevelIsFullyMarked()
    {
      var cells = new byte[GroundLayerCells.ByteLength(ChunkSize)];
      cells[0] = 0x0F;

      Assert.Equal(1f, GroundLayerCells.UnitAt(cells, ChunkSize, 0, 0));
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
