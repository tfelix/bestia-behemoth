using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Builds the mid-distance stand-in for a group of tufts: a splayed, opaque crown of about twenty triangles.
  /// </summary>
  /// <remarks>
  /// <b>Distance costs triangles per pixel, not triangles per tuft.</b> A 72-triangle tuft rasterised into the
  /// eight pixels it covers at 100 m is most of a triangle per pixel of pure waste, and there are tens of
  /// thousands of them. One of these covers the ground of <see cref="TerrainGrass.TuftsPerTussock"/> tufts for
  /// nineteen triangles.
  ///
  /// <para>
  /// Opaque, because <c>grass.gdshader</c> has no <c>ALPHA</c> at all - crossed alpha billboards, the usual
  /// answer here, would take a wide band of the screen off the opaque path. A splayed crown keeps a clumpy
  /// silhouette without one.
  /// </para>
  ///
  /// <para>
  /// Authored to <see cref="TerrainGrass"/>'s own natural height and to the field material's UV convention
  /// (V at the base), so the scatter's existing per-instance transforms place it correctly and it shares the
  /// tufts' material - which is what makes the two tiers the same colour without either knowing about the other.
  /// </para>
  /// </remarks>
  public static class GrassTussock
  {
    /// <summary>Radial segments. Seven is the fewest that still reads as round rather than as a prism.</summary>
    private const int Segments = 7;

    /// <summary>Triangles the built mesh carries: two per segment, plus the crown's fan.</summary>
    public const int Triangles = Segments * 2 + (Segments - 2);

    /// <summary>Widest radius, as a share of the natural height. Wider than tall, which is the whole point.</summary>
    public const float WidthOverHeight = 1.0f;

    private const float CrownRadius = WidthOverHeight;

    /// <summary>Radius where it meets the ground, as a share of <see cref="CrownRadius"/>.</summary>
    private const float BaseRadius = 0.55f;

    /// <summary>Lowest a crown vertex sits, as a share of the natural height. The rest is jitter up to 1.</summary>
    private const float CrownFloor = 0.7f;

    /// <summary>
    /// A crown standing <paramref name="naturalHeight"/> metres tall, to be scaled by the caller's transforms.
    /// </summary>
    public static ArrayMesh Build(float naturalHeight)
    {
      Shape(naturalHeight, out var vertices, out var normals, out var uvs, out var indices);

      var arrays = new Godot.Collections.Array();
      arrays.Resize((int)Godot.Mesh.ArrayType.Max);
      arrays[(int)Godot.Mesh.ArrayType.Vertex] = vertices;
      arrays[(int)Godot.Mesh.ArrayType.Normal] = normals;
      arrays[(int)Godot.Mesh.ArrayType.TexUV] = uvs;
      arrays[(int)Godot.Mesh.ArrayType.Index] = indices;

      var mesh = new ArrayMesh();
      mesh.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays);

      return mesh;
    }

    /// <summary>The crown as plain arrays, the split <see cref="Mesh.ChunkSurface"/> takes and for its reason.</summary>
    /// <remarks>
    /// <c>ArrayMesh</c> cannot be constructed without the engine and <c>BestiaClient.Tests</c> runs without
    /// one, so geometry left inside <see cref="Build"/> is geometry that can only be judged by eye.
    /// </remarks>
    public static void Shape(
      float naturalHeight,
      out Vector3[] vertices,
      out Vector3[] normals,
      out Vector2[] uvs,
      out int[] indices)
    {
      var height = Mathf.Max(naturalHeight, 0.01f);
      var crown = CrownRadius * height;

      vertices = new Vector3[Segments * 2];
      normals = new Vector3[Segments * 2];
      uvs = new Vector2[Segments * 2];

      var tallest = 0.0f;
      var widest = 0.0f;

      for (var i = 0; i < Segments; i++)
      {
        var angle = Mathf.Tau * i / Segments;
        var around = new Vector3(Mathf.Cos(angle), 0.0f, Mathf.Sin(angle));

        // Jittered so the silhouette is ragged rather than a lampshade, and hashed rather than drawn from an
        // Rng so every client builds the same mesh.
        var ragged = Wobble(i);

        var reach = crown * (0.85f + 0.15f * ragged);
        var top = height * (CrownFloor + (1.0f - CrownFloor) * ragged);

        vertices[i] = around * (crown * BaseRadius);
        vertices[Segments + i] = around * reach + new Vector3(0.0f, top, 0.0f);

        tallest = Mathf.Max(tallest, top);
        widest = Mathf.Max(widest, reach);

        // The field material sets `uv_v_at_tip`, so the shader reads the gradient as `1 - UV.y`: nought at the
        // crown is the bright tip colour, one at the base is the dark root colour.
        uvs[i] = new Vector2(i / (float)Segments, 1.0f);
        uvs[Segments + i] = new Vector2(i / (float)Segments, 0.0f);
      }

      // Stretched back to the size that was asked for. The jitter only ever takes away, so without this a crown
      // stands short of the tuft it replaces - and TerrainGrass scales it by that tuft's own natural height,
      // with nothing in the transform to say the mesh disagreed.
      var lift = tallest > 0.0f ? height / tallest : 1.0f;
      var spread = widest > 0.0f ? crown / widest : 1.0f;

      for (var i = 0; i < vertices.Length; i++)
      {
        vertices[i] = new Vector3(vertices[i].X * spread, vertices[i].Y * lift, vertices[i].Z * spread);
      }

      indices = new int[Triangles * 3];
      var at = 0;

      // Wound to face outward and up. The crown flares, so its sides look outward and *downward*, and
      // grass.gdshader writes no normal of its own - the interpolated one is the whole of how a plant is lit.
      for (var i = 0; i < Segments; i++)
      {
        var next = (i + 1) % Segments;

        at = Face(indices, at, i, Segments + next, next);
        at = Face(indices, at, i, Segments + i, Segments + next);
      }

      for (var i = 1; i < Segments - 1; i++)
      {
        at = Face(indices, at, Segments, Segments + i + 1, Segments + i);
      }

      Accumulate(vertices, indices, normals);
    }

    private static int Face(int[] indices, int at, int a, int b, int c)
    {
      indices[at] = a;
      indices[at + 1] = b;
      indices[at + 2] = c;

      return at + 3;
    }

    /// <summary>Smooth normals from the summed face normals, so the crown lights as a mass and not as facets.</summary>
    private static void Accumulate(Vector3[] vertices, int[] indices, Vector3[] normals)
    {
      for (var i = 0; i + 2 < indices.Length; i += 3)
      {
        var a = indices[i];
        var b = indices[i + 1];
        var c = indices[i + 2];

        var face = (vertices[b] - vertices[a]).Cross(vertices[c] - vertices[a]);

        normals[a] += face;
        normals[b] += face;
        normals[c] += face;
      }

      for (var i = 0; i < normals.Length; i++)
      {
        normals[i] = normals[i].LengthSquared() > 0.0f ? normals[i].Normalized() : Vector3.Up;
      }
    }

    /// <summary>A stable value in [0, 1) for one segment.</summary>
    private static float Wobble(int index)
    {
      var x = (uint)index * 2654435761u;

      x ^= x >> 15;
      x *= 0x2C1B3C6Du;
      x ^= x >> 12;

      return (x >> 8) * (1.0f / 16777216.0f);
    }
  }
}
