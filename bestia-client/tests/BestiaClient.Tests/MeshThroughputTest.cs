using System;
using System.Diagnostics;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;
using Xunit.Abstractions;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// What meshing a whole view volume costs, measured rather than argued about.
  /// </summary>
  /// <remarks>
  /// Traited as a benchmark and excluded from the default run: run it deliberately when changing the mesher,
  /// <c>./gradlew clientBenchmark</c>.
  ///
  /// <para>
  /// It does still assert, but only against a ceiling loose enough that only a change in the algorithm's shape can
  /// breach it. The first version of this mesher averaged 11 ms per chunk because the corner average was eight
  /// reads per lattice point rather than a separable pair of adds; that is the class of regression this catches, not
  /// a twenty percent drift.
  /// </para>
  ///
  /// <para>
  /// The ceilings are not in milliseconds but in cell walks: one plain pass over every cell of a chunk, timed right
  /// after the work on the same thread. A busy machine, a slower one, or an efficiency core slows both alike, so the
  /// ratio is what the code makes it. A budget in milliseconds failed whenever the box was busy.
  /// </para>
  /// </remarks>
  [Trait("Category", "Benchmark")]
  [Collection(nameof(MeshThroughputTest))]
  public class MeshThroughputTest
  {
    /// <summary>The fixture palette, which is where WATER gets a surface of its own.</summary>
    private static readonly BlockAppearance Appearance = TerrainFixtures.Appearance();

    private readonly ITestOutputHelper _output;

    /// <summary>Written by every cell walk, so the JIT cannot drop the walk as dead code.</summary>
    private static long _walkSink;

    public MeshThroughputTest(ITestOutputHelper output)
    {
      _output = output;
    }

    [Fact]
    public void MeshesAViewVolumeWithinBudget()
    {
      const int radius = 5;

      var source = new FakeChunkSource();

      // One chunk wider than the measured disc, so no chunk in it is meshed against a missing neighbour.
      for (var chunkY = -(radius + 1); chunkY <= radius + 1; chunkY++)
      {
        for (var chunkX = -(radius + 1); chunkX <= radius + 1; chunkX++)
        {
          source.Put(TerrainFixtures.Rolling(chunkX, chunkY));
        }
      }

      var appearance = TerrainFixtures.Appearance();

      // Warm the JIT and grow the thread-local scratch buffers before the clock starts.
      SurfaceNets.Build(source, new ChunkKey(0, 0, 0), appearance, 1.0f, ChunkWrap.None);
      WalkCells(source.Get(new ChunkKey(0, 0, 0)));

      long triangles = 0;
      var meshed = 0;
      var walks = 0.0;
      var milliseconds = 0.0;

      for (var chunkY = -radius; chunkY <= radius; chunkY++)
      {
        for (var chunkX = -radius; chunkX <= radius; chunkX++)
        {
          var key = new ChunkKey(chunkX, chunkY, 0);
          var mesh = SurfaceNets.Build(source, key, appearance, 1.0f, ChunkWrap.None);
          if (mesh == null)
          {
            continue;
          }

          meshed++;
          // Every surface, counted through the array rather than by naming two of them. The budget is about how
          // many triangles the whole view costs, and adding a third kind while still adding up only two would have
          // quietly under-reported it - which is the failure mode that lets a budget drift past its own limit.
          for (var kind = 0; kind < BlockAppearance.SurfaceKinds; kind++)
          {
            triangles += mesh.Surfaces[kind]?.TriangleCount ?? 0;
          }

          var cost = CostInCellWalks(
            () => SurfaceNets.Build(source, key, appearance, 1.0f, ChunkWrap.None), source.Get(key), pairs: 5
          );
          walks += cost.Walks;
          milliseconds += cost.Milliseconds;
        }
      }

      var chunks = (2 * radius + 1) * (2 * radius + 1);
      var walksPerChunk = walks / Math.Max(1, meshed);

      _output.WriteLine($"view volume: {chunks} chunks, {meshed} with geometry");
      _output.WriteLine(
        $"{walksPerChunk:F1} cell walks per meshed chunk, {milliseconds / Math.Max(1, meshed):F2} ms on this run");
      _output.WriteLine($"{triangles} triangles for the whole view");

      Assert.Equal(chunks, meshed);

      // Around 16 walks per chunk, 2 ms on a developer machine; a busy one measured up to 22. Forty is a
      // regression in kind, not in degree: the eight-read corner average was over five times the cost.
      Assert.True(walksPerChunk < 40.0, $"{walksPerChunk:F1} cell walks per chunk is well past the expected ~16");
    }

    [Fact]
    public void ScansBandsFastEnoughToDoItOnEveryArrival()
    {
      var chunk = TerrainFixtures.Rolling(0, 0);

      ChunkBands.Of(chunk, Appearance);
      WalkCells(chunk);

      var cost = CostInCellWalks(() => ChunkBands.Of(chunk, Appearance), chunk, pairs: 51);

      _output.WriteLine(
        $"band scan {cost.Walks:F2} cell walks, {cost.Milliseconds * 1000.0:F0} us on this run " +
        $"({TerrainFixtures.Size * TerrainFixtures.Size} columns of {TerrainFixtures.Height})");

      // Roughly 0.85 walks, about 160 us, which is what makes rescanning on every decode and every patch a
      // non-decision: a whole view volume is a few tens of milliseconds of scanning, once, on a worker thread. A
      // per-cell loop over 262 144 cells would be two orders of magnitude worse, and staying off one is what the
      // budget guards.
      //
      // It was ten times cheaper before ChunkBands gained its horizontal pass: there are twice as many adjacent
      // column pairs as columns, and each pair has to be compared rather than merely walked. Worth it - without it
      // a vertical rock face has no run boundary anywhere near it, so cave walls and cliffs came out with holes
      // through into the void. Both passes are vectorised; with SIMD switched off (DOTNET_EnableHWIntrinsic=0)
      // the scan costs 2.6 walks. If this fails, the question is which pass stopped being vectorised.
      Assert.True(cost.Walks < 1.75, $"{cost.Walks:F2} cell walks per band scan suggests a scan stopped vectorising");
    }

    /// <summary>
    /// The median ratio of [work] to a walk over [reference]'s cells, each pair timed back to back. The median, so a
    /// pair the scheduler cut through does not count. The milliseconds are only printed: they depend on the machine.
    /// </summary>
    private static (double Walks, double Milliseconds) CostInCellWalks(Action work, VoxelChunk reference, int pairs)
    {
      var ratios = new double[pairs];
      var workTimes = new double[pairs];
      for (var i = 0; i < pairs; i++)
      {
        var workTime = Time(work);
        var walkTime = Time(() => WalkCells(reference));
        ratios[i] = workTime / walkTime;
        workTimes[i] = workTime;
      }

      Array.Sort(ratios);
      Array.Sort(workTimes);

      return (ratios[pairs / 2], workTimes[pairs / 2]);
    }

    /// <summary>
    /// The unit the ceilings are written in: one pass over every cell of a chunk, adding its block and occupancy.
    /// Plain scalar code, so it costs what the machine's memory and core cost, and nothing clever.
    /// </summary>
    private static void WalkCells(VoxelChunk chunk)
    {
      var blocks = chunk.Blocks;
      var occupancy = chunk.Occupancy;
      long sum = 0;
      for (var i = 0; i < blocks.Length; i++)
      {
        sum += blocks[i] + occupancy[i];
      }

      _walkSink += sum;
    }

    private static double Time(Action action)
    {
      var watch = Stopwatch.StartNew();
      action();
      watch.Stop();

      return watch.Elapsed.TotalMilliseconds;
    }
  }

  /// <summary>Keeps <see cref="MeshThroughputTest"/> off the cores the rest of the suite runs on.</summary>
  [CollectionDefinition(nameof(MeshThroughputTest), DisableParallelization = true)]
  public class MeshThroughputCollection
  {
  }
}
