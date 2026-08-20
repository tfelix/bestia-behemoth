using System;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// Meshing the last column of a wrapped world, where the chunk to the east is the chunk at zero.
  /// </summary>
  /// <remarks>
  /// The server normalises every address it touches, so the terrain it serves is continuous across the seam. The
  /// client derives its own neighbour addresses and, before honouring the wrap, derived one past the edge: a
  /// chunk it is never sent, recorded as a missing neighbour, with its own edge column clamped in place instead.
  /// The result is a flat wall down the seam that no message can fix, because nothing is actually absent.
  ///
  /// <para>
  /// A four-chunk world rather than a one- or two-chunk one on purpose: folding by an extent of 1 or 2 is right
  /// for the wrong reasons - <c>floorMod(anything, 1)</c> is always zero - and would let a broken fold pass.
  /// </para>
  /// </remarks>
  public class TerrainSeamTest
  {
    private const int Across = 4;
    private const int LastColumn = Across - 1;

    private static ChunkWrap Wrap => new(Across, Across, true, true);

    /// <summary>The last column and the first, both held, as a player standing at the seam would have them.</summary>
    private static FakeChunkSource AtTheSeam()
    {
      var source = new FakeChunkSource();

      source.Put(TerrainFixtures.Flat(LastColumn, 0, 0, 40.3));
      source.Put(TerrainFixtures.Flat(0, 0, 0, 40.3));

      return source;
    }

    /// <summary>
    /// The payload: the chunk across the seam is read, so it is not reported absent.
    /// </summary>
    /// <remarks>
    /// Asserted against the eastern neighbour specifically rather than against an empty list, because the other
    /// three sides genuinely are not held in this fixture and correctly are missing. Naming the one under test is
    /// what keeps this from passing for the wrong reason.
    /// </remarks>
    [Fact]
    public void The_chunk_across_the_seam_is_not_reported_missing()
    {
      var patch = TerrainPatch.Gather(AtTheSeam(), new ChunkKey(LastColumn, 0, 0), Wrap);

      Assert.NotNull(patch);
      Assert.DoesNotContain(new ChunkKey(0, 0, 0), patch.MissingNeighbours);
    }

    /// <summary>
    /// Nothing off the edge of the world is ever named, however the fold happened to land.
    /// </summary>
    /// <remarks>
    /// The other half of why the address matters: <c>MissingNeighbours</c> is compared against the keys the
    /// server sends, and those are canonical. An entry spelled <c>(4, 0, 0)</c> can never match one, so the debt
    /// it records is never paid and the seam stays clamped even once the neighbour arrives.
    /// </remarks>
    [Fact]
    public void Every_recorded_neighbour_is_inside_the_world()
    {
      var patch = TerrainPatch.Gather(AtTheSeam(), new ChunkKey(LastColumn, 0, 0), Wrap);

      Assert.NotNull(patch);
      Assert.All(patch.MissingNeighbours, key =>
      {
        Assert.InRange(key.X, 0, Across - 1);
        Assert.InRange(key.Y, 0, Across - 1);
      });
    }

    /// <summary>
    /// The same fixture without a wrap, so the test above is shown to be testing the fold and not the fixture.
    /// </summary>
    /// <remarks>
    /// This is the behaviour that shipped, and it is correct for a world with edges - which is why the fold is a
    /// property of the world rather than something the mesher should assume either way.
    /// </remarks>
    [Fact]
    public void Without_a_wrap_the_neighbour_past_the_edge_is_named_off_the_world()
    {
      var patch = TerrainPatch.Gather(AtTheSeam(), new ChunkKey(LastColumn, 0, 0), ChunkWrap.None);

      Assert.NotNull(patch);
      Assert.Contains(patch.MissingNeighbours, key => key.X == Across);
    }

    /// <summary>
    /// A chunk in the middle of the world meshes identically either way, so the fold costs nothing away from a seam.
    /// </summary>
    [Fact]
    public void Away_from_the_seam_the_wrap_changes_nothing()
    {
      var source = new FakeChunkSource();
      for (var dy = -1; dy <= 1; dy++)
      {
        for (var dx = -1; dx <= 1; dx++)
        {
          source.Put(TerrainFixtures.Flat(1 + dx, 1 + dy, 0, 40.3));
        }
      }

      var wrapped = TerrainPatch.Gather(source, new ChunkKey(1, 1, 0), Wrap);
      var plain = TerrainPatch.Gather(source, new ChunkKey(1, 1, 0), ChunkWrap.None);

      Assert.NotNull(wrapped);
      Assert.NotNull(plain);
      Assert.Empty(wrapped.MissingNeighbours);
      Assert.Empty(plain.MissingNeighbours);
      Assert.True(wrapped.Occupancies.SequenceEqual(plain.Occupancies));
      Assert.True(wrapped.Blocks.SequenceEqual(plain.Blocks));
    }
  }
}
