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
  /// Traited as a benchmark and excluded from the default run, because a wall-clock assertion on shared CI hardware
  /// is a test that fails for reasons that have nothing to do with the code. Run it deliberately when changing the
  /// mesher: <c>./gradlew clientBenchmark</c>.
  ///
  /// <para>
  /// It does still assert, but only against a ceiling loose enough that only a change in the algorithm's shape can
  /// breach it. The first version of this mesher averaged 11 ms per chunk because the corner average was eight
  /// reads per lattice point rather than a separable pair of adds; that is the class of regression this catches, not
  /// a twenty percent drift.
  /// </para>
  /// </remarks>
  [Trait("Category", "Benchmark")]
  public class MeshThroughputTest
  {
    private readonly ITestOutputHelper _output;

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
      SurfaceNets.Build(source, new ChunkKey(0, 0, 0), appearance, 1.0f);

      var watch = Stopwatch.StartNew();

      long triangles = 0;
      var meshed = 0;

      for (var chunkY = -radius; chunkY <= radius; chunkY++)
      {
        for (var chunkX = -radius; chunkX <= radius; chunkX++)
        {
          var mesh = SurfaceNets.Build(source, new ChunkKey(chunkX, chunkY, 0), appearance, 1.0f);
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
        }
      }

      watch.Stop();

      var chunks = (2 * radius + 1) * (2 * radius + 1);
      var perChunk = watch.Elapsed.TotalMilliseconds / Math.Max(1, meshed);

      _output.WriteLine($"view volume: {chunks} chunks, {meshed} with geometry");
      _output.WriteLine(
        $"single-threaded total {watch.Elapsed.TotalMilliseconds:F1} ms, {perChunk:F2} ms per meshed chunk");
      _output.WriteLine($"{triangles} triangles for the whole view");

      Assert.Equal(chunks, meshed);

      // Around 2 ms per chunk on a developer machine in Release. Five is a regression in kind, not in degree.
      Assert.True(perChunk < 5.0, $"{perChunk:F2} ms per chunk is well past the expected ~2 ms");
    }

    [Fact]
    public void ScansBandsFastEnoughToDoItOnEveryArrival()
    {
      var chunk = TerrainFixtures.Rolling(0, 0);

      ChunkBands.Of(chunk);

      var watch = Stopwatch.StartNew();

      const int runs = 200;
      for (var i = 0; i < runs; i++)
      {
        ChunkBands.Of(chunk);
      }

      watch.Stop();

      var micros = watch.Elapsed.TotalMilliseconds / runs * 1000.0;

      _output.WriteLine(
        $"band scan {micros:F0} us per chunk " +
        $"({TerrainFixtures.Size * TerrainFixtures.Size} columns of {TerrainFixtures.Height})");

      // Roughly 330 us, which is still what makes rescanning on every decode and every patch a non-decision: a
      // whole view volume is about 40 ms of scanning, once, on a worker thread. A per-cell loop over 262 144
      // cells would be two orders of magnitude worse, and staying off one is what the budget guards.
      //
      // It was ~30 us before ChunkBands gained its horizontal pass, and the tenfold is that pass: there are
      // twice as many adjacent column pairs as columns, and each pair has to be compared rather than merely
      // walked. Worth it - without it a vertical rock face has no run boundary anywhere near it, so cave walls
      // and cliffs came out with holes through into the void. Both passes are vectorised; if this ever fails,
      // the question is which one stopped being.
      Assert.True(micros < 500.0, $"{micros:F0} us per band scan suggests a scan stopped vectorising");
    }
  }
}
