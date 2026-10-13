using System;
using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// What the single owner of the ground marks lays from a footfall or a death, and how a server snapshot
  /// replaces it.
  /// </summary>
  /// <remarks>
  /// The arithmetic underneath is pinned in <c>GroundSpreadTest</c>, <c>ClientColumnLevelsTest</c> and
  /// <c>ClientColumnStampsTest</c>; this is about the wiring - which columns are touched, what the renderer is
  /// told, and what a per-layer replace leaves alone. Block ids are the server's: grass 13, stone 7.
  /// </remarks>
  public class ClientGroundMarksTest
  {
    private const int Size = 32;
    private const int Grass = 13;
    private const int Stone = 7;
    private const long Lifetime = 1_200_000;

    private static readonly ChunkKey Origin = new(0, 0, 0);
    private static readonly ChunkKey West = new(-1, 0, 0);

    private sealed class FakeWorld : IGroundWorld
    {
      internal readonly HashSet<ChunkKey> Held = new() { Origin, West, new ChunkKey(0, -1, 0) };

      internal Func<long, long, int, int> Blocks = (x, y, z) => Grass;

      public long NowMillis { get; set; } = 1_000_000;

      public long NowSecond { get; set; } = 500_000;

      public bool HoldsColumn(ChunkKey column) => Held.Contains(column);

      public int SurfaceBlockAt(long voxelX, long voxelY, int nearZ) => Blocks(voxelX, voxelY, nearZ);
    }

    private sealed class RecordingSink : IGroundMarkSink
    {
      internal readonly List<(ChunkKey Column, GroundStamp Stamp)> Pressed = new();
      internal readonly List<ChunkKey> StampsChanged = new();
      internal readonly List<(ChunkKey Column, int Cell, GroundLayers.Id Layer, int Level)> LevelChanges = new();
      internal readonly List<ChunkKey> LevelsChanged = new();
      internal readonly List<ChunkKey> Dropped = new();

      void IGroundMarkSink.StampPressed(ChunkKey column, GroundStamp stamp) => Pressed.Add((column, stamp));

      void IGroundMarkSink.StampsChanged(ChunkKey column) => StampsChanged.Add(column);

      void IGroundMarkSink.LevelChanged(ChunkKey column, int cellIndex, GroundLayers.Id layer, int level) =>
        LevelChanges.Add((column, cellIndex, layer, level));

      void IGroundMarkSink.LevelsChanged(ChunkKey column) => LevelsChanged.Add(column);

      void IGroundMarkSink.ColumnDropped(ChunkKey column) => Dropped.Add(column);
    }

    private static GroundRules Rules() => new(
      Lifetime, 48, new[] { 10, 11, 12, 13, 14, 15, 16 },
      new WearRules(
        259_200, 32, 6, 2,
        new Dictionary<int, double> { [11] = 0.4, [12] = 0.6, [13] = 1.0, [14] = 0.8, [16] = 0.9 }),
      new BloodRules(432_000, 16, 220, 2));

    private static (ClientGroundMarks Marks, FakeWorld World, RecordingSink Sink) Marked(bool rules = true)
    {
      var world = new FakeWorld();
      var sink = new RecordingSink();
      var marks = new ClientGroundMarks(Size, world, sink);

      if (rules)
      {
        marks.SetRules(Rules());
      }

      return (marks, world, sink);
    }

    private static List<GroundStamp> Stamps(ClientGroundMarks marks)
    {
      var found = new List<GroundStamp>();
      marks.ForEachStamp((column, stamp) => found.Add(stamp));

      return found;
    }

    private static byte[] Levels(params (int Cell, byte Level)[] cells)
    {
      var levels = new byte[Size * Size];
      foreach (var (cell, level) in cells)
      {
        levels[cell] = level;
      }

      return levels;
    }

    [Fact]
    public void NothingIsLaidBeforeTheRulesArrive()
    {
      var (marks, _, sink) = Marked(rules: false);

      marks.Footfall(1, -1, 0, 0, 0, 40);
      marks.Death(5, 5);
      var merge = marks.ApplyStamps(Origin, new[] { new GroundStamp(0, 1, 0, 0, 255) });

      Assert.Equal(0, merge.Prior);
      Assert.Empty(sink.Pressed);
      Assert.Empty(sink.LevelChanges);
      Assert.Empty(sink.StampsChanged);
      Assert.Empty(marks.Columns);
    }

    [Fact]
    public void AFootfallOntoGrassLaysAPrintHeadedTheWayTheStepWent()
    {
      var (marks, _, sink) = Marked();

      // Tile (3, -3) is cell 29 * 32 + 3 of column (0, -1). Seed 217 is contract.md's (77, 3, -3); a step from
      // (2, -2) is one towards +x and one towards -y, which is octant 7.
      marks.Footfall(77, 2, -2, 3, -3, 40);

      var (column, stamp) = Assert.Single(sink.Pressed);
      Assert.Equal(new ChunkKey(0, -1, 0), column);
      Assert.Equal(931, stamp.CellIndex);
      Assert.Equal(GroundStampCells.Footprint, stamp.Kind);
      Assert.Equal(7, stamp.Octant);
      Assert.Equal(217, stamp.Seed);
      Assert.Equal(255, stamp.Strength);
    }

    [Fact]
    public void ThePrintIsInTheRingTheNextRebuildReadsFrom()
    {
      var (marks, _, _) = Marked();

      marks.Footfall(1, -1, 0, 0, 0, 40);

      var stamp = Assert.Single(Stamps(marks));
      Assert.Equal(0, stamp.CellIndex);
      Assert.Equal(120, stamp.Seed);
    }

    [Fact]
    public void TheSameFootfallReportedTwiceIsOnePrintButTwiceTheWear()
    {
      var (marks, _, sink) = Marked();

      marks.Footfall(1, -1, 0, 0, 0, 40);
      marks.Footfall(1, -1, 0, 0, 0, 40);

      Assert.Single(sink.Pressed);

      // Wear is not deduplicated, as the server's is not: 12 on the tile, still under the threshold of 32.
      Assert.Null(marks.CellsOf(Origin));
    }

    [Theory]
    [InlineData(Stone)]
    [InlineData(-1)]
    public void AStepOntoGroundThatTakesNothingLaysNothing(int block)
    {
      var (marks, world, sink) = Marked();
      world.Blocks = (x, y, z) => block;

      marks.Footfall(1, -1, 0, 0, 0, 40);

      Assert.Empty(sink.Pressed);
      Assert.Empty(marks.Columns);
    }

    [Fact]
    public void WearIsReportedOnceItCrossesTheThresholdAndNotBefore()
    {
      var (marks, world, sink) = Marked();

      for (var step = 1; step <= 5; step++)
      {
        marks.Footfall(1, -1, 0, 0, 0, 40);
        world.NowMillis += 5_000;
      }

      // 5 * 6 = 30 on the tile, under the threshold of 32.
      Assert.Empty(sink.LevelChanges);

      marks.Footfall(1, -1, 0, 0, 0, 40);

      // 36 is level 2 once the top four bits are read.
      var change = Assert.Single(sink.LevelChanges);
      Assert.Equal((Origin, 0, GroundLayers.Id.Worn, 2), change);
      Assert.Equal(2, GroundLayerCells.LevelAt(marks.CellsOf(Origin)[1], Size, 0, 0));
    }

    [Fact]
    public void TheNeighboursWearTooAcrossTheColumnEdge()
    {
      var (marks, _, _) = Marked();

      for (var step = 0; step < 16; step++)
      {
        marks.Footfall(1, -1, 0, 0, 0, 40);
      }

      // 16 * 2 = 32 on the tile west of the origin, which is cell 31 of the next column over.
      Assert.Equal(2, GroundLayerCells.LevelAt(marks.CellsOf(West)[1], Size, 31, 0));
    }

    /// <summary>The client knows the block of a tile only in a chunk it holds, and the server wears only held columns.</summary>
    [Fact]
    public void WearIsLaidOnlyWhereTheGroundIsKnown()
    {
      var (marks, world, _) = Marked();
      world.Blocks = (x, y, z) => x < 0 ? -1 : Grass;

      for (var step = 0; step < 16; step++)
      {
        marks.Footfall(1, -1, 0, 0, 0, 40);
      }

      Assert.Null(marks.CellsOf(West));
      Assert.NotNull(marks.CellsOf(Origin));
    }

    [Fact]
    public void WearNeedsTheWorldClock()
    {
      var (marks, world, sink) = Marked();
      world.NowSecond = -1;

      for (var step = 0; step < 16; step++)
      {
        marks.Footfall(1, -1, 0, 0, 0, 40);
      }

      Assert.Single(sink.Pressed);
      Assert.Empty(sink.LevelChanges);
      Assert.Null(marks.CellsOf(Origin));
    }

    [Fact]
    public void ATileAcrossTheSeamIsInTheColumnTheServerNames()
    {
      var (marks, world, sink) = Marked();
      marks.Wrap = new ChunkWrap(10, 10, true, true);
      world.Held.Add(new ChunkKey(9, 0, 0));

      marks.Footfall(1, 0, 0, -1, 0, 40);

      var (column, stamp) = Assert.Single(sink.Pressed);
      Assert.Equal(new ChunkKey(9, 0, 0), column);
      Assert.Equal(31, stamp.CellIndex);
    }

    [Fact]
    public void ASnapshotReplacesThePrintsLaidLocally()
    {
      var (marks, world, sink) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);
      world.NowMillis += 10_000;

      var merge = marks.ApplyStamps(Origin, new[] { new GroundStamp(40, 1, 2, 7, 255) });

      Assert.Equal(1, merge.Prior);
      Assert.Equal(1, merge.LocalDropped);
      Assert.Equal(1, merge.SnapshotNew);
      Assert.Contains(Origin, sink.StampsChanged);

      var stamp = Assert.Single(Stamps(marks));
      Assert.Equal(40, stamp.CellIndex);
    }

    [Fact]
    public void AnEmptySnapshotOfAColumnWithNothingIsNotWorthARedraw()
    {
      var (marks, _, sink) = Marked();

      marks.ApplyStamps(Origin, new GroundStamp[0]);

      Assert.Empty(sink.StampsChanged);
    }

    [Fact]
    public void ALayerIsReplacedOnItsOwnAndTheOthersAreLeftAlone()
    {
      var (marks, _, sink) = Marked();
      var scorch = new byte[GroundLayerCells.ByteLength(Size)];
      scorch[0] = 0x0F;

      marks.ApplyLayers(Origin, new[]
      {
        GroundLayerEntry.FromLevels(GroundLayers.Id.Worn, Levels((5, 200)), 400_000),
        GroundLayerEntry.FromNibbles(GroundLayers.Id.Scorched, scorch)
      });

      // A message that names only the scorch retires it and leaves the wear as it was.
      marks.ApplyLayers(Origin, new[] { GroundLayerEntry.Retire(GroundLayers.Id.Scorched) });

      var cells = marks.CellsOf(Origin);
      Assert.Null(cells[GroundLayers.ChannelOf(GroundLayers.Id.Scorched)]);

      // 200 is level 12 by its top four bits, and above the threshold of 32.
      Assert.Equal(12, GroundLayerCells.LevelAt(cells[GroundLayers.ChannelOf(GroundLayers.Id.Worn)], Size, 5, 0));
      Assert.Equal(2, sink.LevelsChanged.Count);
    }

    [Fact]
    public void RetiringTheLastLayerForgetsTheColumn()
    {
      var (marks, _, _) = Marked();
      marks.ApplyLayers(Origin, new[] { GroundLayerEntry.FromLevels(GroundLayers.Id.Worn, Levels((5, 200)), 0) });

      marks.ApplyLayers(Origin, new[] { GroundLayerEntry.Retire(GroundLayers.Id.Worn) });

      Assert.Null(marks.CellsOf(Origin));
      Assert.Empty(marks.Columns);
    }

    [Fact]
    public void ASnapshotOfLevelsReportsHowFarTheDerivedOnesHadDrifted()
    {
      var (marks, _, _) = Marked();
      for (var step = 0; step < 16; step++)
      {
        marks.Footfall(1, -1, 0, 0, 0, 40);
      }

      // The footfalls put 96 on cell 0; the server says 40.
      var merge = marks.ApplyLayers(
        Origin, new[] { GroundLayerEntry.FromLevels(GroundLayers.Id.Worn, Levels((0, 40)), 500_000) });

      Assert.True(merge.Worn.Prior > 0);
      Assert.True(merge.Worn.Drifted > 0);
    }

    [Fact]
    public void WearFadesWithTheWorldClockAndTheColumnIsRedrawn()
    {
      var (marks, world, sink) = Marked();
      marks.ApplyLayers(
        Origin, new[] { GroundLayerEntry.FromLevels(GroundLayers.Id.Worn, Levels((0, 255)), 0) });
      sink.LevelsChanged.Clear();

      // Half the 259200 s fade: contract.md's 128 for a full cell.
      world.NowSecond = 129_600;
      marks.AgeLevels();

      Assert.Equal(new[] { Origin }, sink.LevelsChanged);
      Assert.Equal(8, GroundLayerCells.LevelAt(marks.CellsOf(Origin)[1], Size, 0, 0));
    }

    [Fact]
    public void ALayerThatHasFadedAwayLeavesNothingBehind()
    {
      var (marks, world, sink) = Marked();
      marks.ApplyLayers(
        Origin, new[] { GroundLayerEntry.FromLevels(GroundLayers.Id.Worn, Levels((0, 255)), 0) });

      world.NowSecond = 2_000_000;
      marks.AgeLevels();

      Assert.Null(marks.CellsOf(Origin));
      Assert.Empty(marks.Columns);
      Assert.Equal(2, sink.LevelsChanged.Count);
    }

    [Fact]
    public void ADeathSoaksThirteenCellsOfBlood()
    {
      var (marks, _, sink) = Marked();

      marks.Death(10, 10);

      Assert.Equal(13, sink.LevelChanges.Count);
      Assert.All(sink.LevelChanges, change => Assert.Equal(GroundLayers.Id.Bloodied, change.Layer));

      // 220, 146, 116 and 73 by their top four bits, on cells (10, 10), (11, 10), (11, 11) and (12, 10).
      Assert.Contains((Origin, 330, GroundLayers.Id.Bloodied, 13), sink.LevelChanges);
      Assert.Contains((Origin, 331, GroundLayers.Id.Bloodied, 9), sink.LevelChanges);
      Assert.Contains((Origin, 363, GroundLayers.Id.Bloodied, 7), sink.LevelChanges);
      Assert.Contains((Origin, 332, GroundLayers.Id.Bloodied, 4), sink.LevelChanges);
    }

    [Fact]
    public void BloodIsLaidOnlyInColumnsTheClientHolds()
    {
      var (marks, world, sink) = Marked();
      world.Held.Remove(West);
      world.Held.Remove(new ChunkKey(0, -1, 0));

      marks.Death(0, 0);

      // Of the thirteen cells, only the six with neither coordinate negative are in the held column.
      Assert.Equal(6, sink.LevelChanges.Count);
      Assert.All(sink.LevelChanges, change => Assert.Equal(Origin, change.Column));
    }

    [Fact]
    public void AColumnNoLongerHeldIsForgotten()
    {
      var (marks, _, sink) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);

      marks.Drop(Origin);
      marks.Drop(new ChunkKey(5, 5, 0));

      Assert.Equal(new[] { Origin }, sink.Dropped);
      Assert.Empty(Stamps(marks));
      Assert.Null(marks.CellsOf(Origin));
    }

    [Fact]
    public void PrintsThatHaveExpiredAreDroppedAndTheColumnIsRedrawn()
    {
      var (marks, world, sink) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);

      world.NowMillis += Lifetime;
      marks.AgeStamps();

      Assert.Empty(Stamps(marks));
      Assert.Contains(Origin, sink.StampsChanged);
    }

    [Fact]
    public void AnExpiredPrintIsNotDrawnEvenBeforeTheNextTickDropsIt()
    {
      var (marks, world, _) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);

      world.NowMillis += Lifetime;

      Assert.Empty(Stamps(marks));
    }

    [Fact]
    public void ARoutineTickRedrawsNothing()
    {
      var (marks, world, sink) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);

      // Past the first level step, then inside the next one.
      world.NowMillis += 1_000;
      marks.AgeStamps();
      sink.StampsChanged.Clear();

      world.NowMillis += 1_000;
      marks.AgeStamps();

      Assert.Empty(sink.StampsChanged);
      Assert.Single(Stamps(marks));
    }

    [Fact]
    public void ClearForgetsTheRecordsButNotTheRules()
    {
      var (marks, _, _) = Marked();
      marks.Footfall(1, -1, 0, 0, 0, 40);

      marks.Clear();

      Assert.Empty(marks.Columns);
      Assert.NotNull(marks.Rules);
    }

    [Fact]
    public void TheColumnsListedAreTheOnesHoldingAnything()
    {
      var (marks, world, _) = Marked();
      world.Blocks = (x, y, z) => x >= 0 && y >= 0 ? Grass : -1;

      marks.Footfall(1, -1, 0, 0, 0, 40);

      Assert.Equal(new[] { Origin }, marks.Columns.ToArray());
    }
  }
}
