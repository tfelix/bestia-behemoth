using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class ClientGroundFireTest
  {
    private const int Size = 32;
    private const int MaskBytes = Size * Size / 8;

    private static readonly ChunkKey Origin = new(0, 0, 0);

    private sealed class RecordingSink : IGroundFireSink
    {
      internal readonly List<ChunkKey> Changed = new();

      public void FireChanged(ChunkKey column) => Changed.Add(column);
    }

    private readonly RecordingSink _sink = new();
    private readonly ClientGroundFire _fire;

    public ClientGroundFireTest()
    {
      _fire = new ClientGroundFire(Size, _sink);
    }

    private static byte[] MaskOf(params int[] cells)
    {
      var mask = new byte[MaskBytes];
      foreach (var cell in cells)
      {
        mask[cell >> 3] |= (byte)(1 << (cell & 7));
      }

      return mask;
    }

    private List<FireCell> CellsOf(ChunkKey column)
    {
      var cells = new List<FireCell>();
      _fire.CellsOf(column, cells);

      return cells.OrderBy(cell => cell.Index).ToList();
    }

    [Fact]
    public void BitOrderIsByteOverEightLeastSignificantFirst()
    {
      // The bytes ColumnMaskTest on the server expects for cells 0, 1, 7, 8 and 32.
      var mask = new byte[MaskBytes];
      mask[0] = 0b1000_0011;
      mask[1] = 0b0000_0001;
      mask[4] = 0b0000_0001;

      _fire.Apply(Origin, mask, 10f);

      Assert.Equal(new[] { 0, 1, 7, 8, 32 }, CellsOf(Origin).Select(cell => cell.Index));
    }

    [Fact]
    public void ACellKeepsItsIgnitionTimeWhileItBurns()
    {
      _fire.Apply(Origin, MaskOf(5), 10f);
      _fire.Apply(Origin, MaskOf(5, 6), 11f);

      var cells = CellsOf(Origin);

      Assert.Equal(10f, cells[0].IgnitedAt);
      Assert.Equal(11f, cells[1].IgnitedAt);
      Assert.True(cells.All(cell => cell.IsBurning));
    }

    [Fact]
    public void ACellWhoseBitClearsIsOutButStillKept()
    {
      _fire.Apply(Origin, MaskOf(5, 6), 10f);
      _fire.Apply(Origin, MaskOf(6), 12f);

      var cells = CellsOf(Origin);

      Assert.Equal(2, cells.Count);
      Assert.Equal(12f, cells[0].OutAt);
      Assert.True(cells[1].IsBurning);
    }

    [Fact]
    public void ACleanMessagePutsEveryCellOutRatherThanForgettingIt()
    {
      _fire.Apply(Origin, MaskOf(5, 6), 10f);
      _fire.Apply(Origin, null, 12f);

      var cells = CellsOf(Origin);

      Assert.Equal(2, cells.Count);
      Assert.All(cells, cell => Assert.Equal(12f, cell.OutAt));
    }

    [Fact]
    public void ARepeatedMaskChangesNothingAndTellsNobody()
    {
      _fire.Apply(Origin, MaskOf(5), 10f);
      _sink.Changed.Clear();

      _fire.Apply(Origin, MaskOf(5), 11f);
      _fire.Apply(new ChunkKey(0, 0, 3), MaskOf(5), 12f);

      Assert.Empty(_sink.Changed);
      Assert.Equal(10f, CellsOf(Origin)[0].IgnitedAt);
    }

    [Fact]
    public void ASlabAddressIsReducedToItsColumn()
    {
      _fire.Apply(new ChunkKey(2, 3, 4), MaskOf(5), 10f);

      Assert.Equal(new[] { new ChunkKey(2, 3, 0) }, _sink.Changed);
      Assert.Single(CellsOf(new ChunkKey(2, 3, 0)));
    }

    [Fact]
    public void TheSweepForgetsOnlyCellsOutLongEnough()
    {
      _fire.Apply(Origin, MaskOf(5, 6), 10f);
      _fire.Apply(Origin, MaskOf(6), 12f);
      _sink.Changed.Clear();

      _fire.Sweep(12f + ClientGroundFire.KeptAfterOutSeconds - 0.1f);
      Assert.Empty(_sink.Changed);

      _fire.Sweep(12f + ClientGroundFire.KeptAfterOutSeconds);
      Assert.Equal(new[] { Origin }, _sink.Changed);
      Assert.Equal(new[] { 6 }, CellsOf(Origin).Select(cell => cell.Index));
    }

    [Fact]
    public void ASweptOutColumnIsGone()
    {
      _fire.Apply(Origin, MaskOf(5), 10f);
      _fire.Apply(Origin, null, 11f);

      _fire.Sweep(11f + ClientGroundFire.KeptAfterOutSeconds);

      Assert.Empty(_fire.Columns);
      Assert.Empty(CellsOf(Origin));
    }

    [Fact]
    public void DroppingAColumnForgetsItAndTellsTheRenderer()
    {
      _fire.Apply(Origin, MaskOf(5), 10f);
      _sink.Changed.Clear();

      _fire.Drop(Origin);

      Assert.Equal(new[] { Origin }, _sink.Changed);
      Assert.Empty(_fire.Columns);
    }
  }
}
