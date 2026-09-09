using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// Thinning a tuft by taking whole blades out of it - the only level of detail that changes no silhouette.
  /// </summary>
  /// <remarks>
  /// The <c>ArrayMesh</c> half of <see cref="GrassBlades"/> cannot be reached without an engine, so what is
  /// pinned here is the array half: the split, the choice of which blades survive, and the index buffer that
  /// comes out. <c>grass2</c>'s own shape is checked at load instead - see <c>TerrainGrass.BuildRungs</c>.
  /// </remarks>
  public class GrassBladesTest
  {
    /// <summary>
    /// A tuft of <paramref name="blades"/> disjoint two-triangle blades, the tallest of them first.
    /// </summary>
    /// <remarks>
    /// Heights run 1, 2, 3... so "which blades survived" is answerable by reading the vertices back, and the
    /// blades are deliberately laid out shortest-first so that keeping the *first* N would fail every test
    /// below that keeping the tallest N passes.
    /// </remarks>
    private static void Tuft(int blades, out Vector3[] vertices, out int[] indices)
    {
      vertices = new Vector3[blades * 4];
      indices = new int[blades * 6];

      for (var b = 0; b < blades; b++)
      {
        var height = b + 1.0f;
        var at = b * 4;

        vertices[at] = new Vector3(b, 0.0f, 0.0f);
        vertices[at + 1] = new Vector3(b + 0.1f, 0.0f, 0.0f);
        vertices[at + 2] = new Vector3(b, height, 0.0f);
        vertices[at + 3] = new Vector3(b + 0.1f, height, 0.0f);

        var t = b * 6;
        indices[t] = at;
        indices[t + 1] = at + 1;
        indices[t + 2] = at + 2;
        indices[t + 3] = at + 1;
        indices[t + 4] = at + 3;
        indices[t + 5] = at + 2;
      }
    }

    [Fact]
    public void TheSplitFindsOneComponentPerBlade()
    {
      Tuft(6, out var vertices, out var indices);

      var label = GrassBlades.Split(vertices, indices, out var blades);

      Assert.Equal(6, blades);

      // Every vertex of a blade carries that blade's label, and no two blades share one.
      for (var b = 0; b < 6; b++)
      {
        var own = label[b * 4];

        Assert.InRange(own, 0, 5);
        Assert.Equal(own, label[b * 4 + 1]);
        Assert.Equal(own, label[b * 4 + 2]);
        Assert.Equal(own, label[b * 4 + 3]);
      }
    }

    /// <summary>
    /// The blades that survive are the tallest ones, because the field's height is calibrated on them.
    /// </summary>
    /// <remarks>
    /// <c>TerrainGrass.NaturalHeight</c> and every scatter transform are measured against how tall the mesh
    /// stands. Keeping the first blades rather than the tallest would shorten the field at exactly the distance
    /// nobody can walk up to and check.
    /// </remarks>
    [Fact]
    public void TheTallestBladesAreTheOnesKept()
    {
      Tuft(6, out var vertices, out var indices);

      Assert.True(GrassBlades.Reduce(vertices, indices, 3, out var reduced));

      var tallest = 0.0f;

      foreach (var index in reduced)
      {
        tallest = Mathf.Max(tallest, vertices[index].Y);
      }

      // Blades stand 1..6; the three tallest are 4, 5 and 6, and the shortest survivor must be 4.
      var shortest = float.PositiveInfinity;

      for (var t = 0; t + 2 < reduced.Length; t += 3)
      {
        var peak = Mathf.Max(
          vertices[reduced[t]].Y, Mathf.Max(vertices[reduced[t + 1]].Y, vertices[reduced[t + 2]].Y));

        shortest = Mathf.Min(shortest, peak);
      }

      Assert.Equal(6.0f, tallest);
      Assert.Equal(4.0f, shortest);
    }

    /// <summary>What comes out is a subset of what went in, at the expected size.</summary>
    [Fact]
    public void TheReducedBufferIsAStrictSubsetOfTheOriginal()
    {
      Tuft(6, out var vertices, out var indices);

      Assert.True(GrassBlades.Reduce(vertices, indices, 2, out var reduced));

      // Two blades of two triangles.
      Assert.Equal(2 * 2 * 3, reduced.Length);

      var original = new System.Collections.Generic.HashSet<string>();

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        original.Add($"{indices[t]},{indices[t + 1]},{indices[t + 2]}");
      }

      for (var t = 0; t + 2 < reduced.Length; t += 3)
      {
        Assert.Contains($"{reduced[t]},{reduced[t + 1]},{reduced[t + 2]}", original);
      }
    }

    /// <summary>
    /// Asking for everything, or for more than there is, is refused rather than answered with a copy.
    /// </summary>
    /// <remarks>
    /// The caller reads null as "draw the whole tuft", so a reduction that saves nothing must not present
    /// itself as a second mesh to swap to - that would cost a mesh assignment per cell for no triangles.
    /// </remarks>
    [Fact]
    public void AReductionThatSavesNothingIsRefused()
    {
      Tuft(6, out var vertices, out var indices);

      Assert.False(GrassBlades.Reduce(vertices, indices, 6, out var same));
      Assert.Null(same);

      Assert.False(GrassBlades.Reduce(vertices, indices, 9, out var more));
      Assert.Null(more);

      Assert.False(GrassBlades.Reduce(vertices, indices, 0, out var none));
      Assert.Null(none);
    }

    /// <summary>
    /// A blade cut along a UV seam is still one blade.
    /// </summary>
    /// <remarks>
    /// <b>This is <c>grass2</c>, and it is what the first version of this got wrong.</b> The real mesh carries
    /// 102 vertices for 66 positions because its blades are split along their own seams, so union-find over the
    /// index buffer alone found nine pieces rather than six - fragments of 2, 4, 6 and 8 triangles, one of
    /// which holds the tallest point in the mesh. <see cref="GrassBlades.Reduce"/> would then have kept
    /// half-blades and the field would have drawn torn grass at distance.
    /// </remarks>
    [Fact]
    public void ASeamDoesNotCutABladeInTwo()
    {
      // One blade, built as two halves that meet along an edge with duplicated vertices - no shared index.
      var vertices = new[]
      {
        new Vector3(0.0f, 0.0f, 0.0f), new Vector3(1.0f, 0.0f, 0.0f), new Vector3(0.0f, 1.0f, 0.0f),
        // The seam: the same two positions again, as far as the index buffer is concerned unrelated.
        new Vector3(1.0f, 0.0f, 0.0f), new Vector3(0.0f, 1.0f, 0.0f), new Vector3(1.0f, 1.0f, 0.0f)
      };

      var indices = new[] { 0, 1, 2, 3, 5, 4 };

      GrassBlades.Split(vertices, indices, out var blades);

      Assert.Equal(1, blades);

      // And a second, taller blade beside it is still told apart from the first.
      var two = new[]
      {
        vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5],
        new Vector3(9.0f, 0.0f, 0.0f), new Vector3(9.5f, 0.0f, 0.0f), new Vector3(9.0f, 5.0f, 0.0f)
      };

      GrassBlades.Split(two, new[] { 0, 1, 2, 3, 5, 4, 6, 7, 8 }, out var pair);

      Assert.Equal(2, pair);
    }

    /// <summary>Art that is not blades at all is refused, so a re-export degrades rather than breaks.</summary>
    [Fact]
    public void AMeshThatIsOnePieceCannotBeThinned()
    {
      Tuft(1, out var vertices, out var indices);

      Assert.False(GrassBlades.Reduce(vertices, indices, 1, out var reduced));
      Assert.Null(reduced);

      Assert.False(GrassBlades.Reduce(null, indices, 2, out _));
      Assert.False(GrassBlades.Reduce(vertices, new[] { 0, 1 }, 1, out _));
    }
  }
}
