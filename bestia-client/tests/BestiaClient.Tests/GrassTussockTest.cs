using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The grass field's mid-distance crown: the shape, and the two facts <c>TerrainGrass</c> places it by.
  /// </summary>
  public class GrassTussockTest
  {
    /// <summary>The same natural height the tuft is authored at, and the field's own scatter scale.</summary>
    private const float Natural = 0.7053f;

    /// <summary>
    /// It stands exactly as tall as the tuft it replaces, so the scatter's transforms already place it.
    /// </summary>
    /// <remarks>
    /// <c>TerrainGrass.Scatter</c> divides each plant's wanted height by one <c>NaturalHeight</c>, and the mid
    /// tier draws a prefix of those very transforms. A crown authored to any other height would come out of
    /// the ground at the wrong size with nothing in the code to say why.
    /// </remarks>
    [Fact]
    public void TheCrownIsTheTuftsOwnHeight()
    {
      GrassTussock.Shape(Natural, out var vertices, out _, out _, out _);

      var top = 0.0f;
      var bottom = 0.0f;

      foreach (var vertex in vertices)
      {
        top = Mathf.Max(top, vertex.Y);
        bottom = Mathf.Min(bottom, vertex.Y);
      }

      Assert.Equal(Natural, top, 4);
      Assert.Equal(0.0f, bottom, 4);
    }

    /// <summary>
    /// It is wider than it is tall, which is the whole of what makes one worth twenty-odd tufts.
    /// </summary>
    [Fact]
    public void TheCrownIsWiderThanItIsTall()
    {
      GrassTussock.Shape(Natural, out var vertices, out _, out _, out _);

      var reach = 0.0f;

      foreach (var vertex in vertices)
      {
        reach = Mathf.Max(reach, new Vector2(vertex.X, vertex.Z).Length());
      }

      Assert.True(reach * 2.0f > Natural, $"{reach * 2.0f} m across against {Natural} m tall is not a crown");
      Assert.Equal(Natural * GrassTussock.WidthOverHeight, reach, 2);
    }

    /// <summary>
    /// It costs what <c>TerrainGrass</c> prices it at, and the count is what the budget is spent in.
    /// </summary>
    [Fact]
    public void TheTriangleCountIsWhatItSaysItIs()
    {
      GrassTussock.Shape(Natural, out var vertices, out _, out _, out var indices);

      Assert.Equal(GrassTussock.Triangles * 3, indices.Length);
      Assert.True(GrassTussock.Triangles < 72 / 3, $"{GrassTussock.Triangles} is not cheap next to a 72-tri tuft");

      foreach (var index in indices)
      {
        Assert.InRange(index, 0, vertices.Length - 1);
      }
    }

    /// <summary>
    /// Every vertex gets a real normal, and they face outward rather than into the crown.
    /// </summary>
    /// <remarks>
    /// <c>grass.gdshader</c> writes only <c>ALBEDO</c>, so the interpolated normal is the whole of how a crown
    /// is lit. A degenerate one is a black plant, and an inward one is a plant lit from the wrong side.
    /// </remarks>
    [Fact]
    public void EveryNormalIsUnitLengthAndPointsOutward()
    {
      GrassTussock.Shape(Natural, out var vertices, out var normals, out _, out _);

      for (var i = 0; i < normals.Length; i++)
      {
        Assert.Equal(1.0f, normals[i].Length(), 3);

        var outward = new Vector3(vertices[i].X, 0.0f, vertices[i].Z);

        if (outward.LengthSquared() > 0.0001f)
        {
          Assert.True(
            normals[i].Dot(outward.Normalized()) > 0.0f,
            $"vertex {i}'s normal points into the crown");
        }
      }
    }

    /// <summary>
    /// V runs 1 at the base to 0 at the crown, which is the convention the field's material is set to.
    /// </summary>
    /// <remarks>
    /// <c>TerrainGrass.Load</c> sets <c>uv_v_at_tip</c> on the material both tiers share, so the shader reads
    /// the gradient as <c>1 - UV.y</c>. Getting it backwards is visible twice over: the plant is dark at the
    /// tip and bright at the root, and it bends from the wrong end.
    /// </remarks>
    [Fact]
    public void VRunsFromTheBaseToTheCrown()
    {
      GrassTussock.Shape(Natural, out var vertices, out _, out var uvs, out _);

      for (var i = 0; i < uvs.Length; i++)
      {
        var expected = vertices[i].Y > 0.0f ? 0.0f : 1.0f;

        Assert.Equal(expected, uvs[i].Y);
      }
    }

    /// <summary>A degenerate height is clamped rather than collapsing the crown to a point.</summary>
    [Fact]
    public void AZeroHeightStillBuildsAMesh()
    {
      GrassTussock.Shape(0.0f, out var vertices, out var normals, out _, out var indices);

      Assert.NotEmpty(vertices);
      Assert.Equal(GrassTussock.Triangles * 3, indices.Length);

      foreach (var normal in normals)
      {
        Assert.Equal(1.0f, normal.Length(), 3);
      }
    }
  }
}
