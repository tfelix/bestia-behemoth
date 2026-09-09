using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Cheaper tufts, made by dropping whole blades out of the one the field is drawn with.
  /// </summary>
  /// <remarks>
  /// <b>The only kind of level of detail that cannot be seen.</b> A distant tuft has to keep the silhouette of
  /// a near one - the eye finds a boundary by shape long before it finds one by colour, which is what a
  /// stand-in of a different shape got wrong - and the surest way to keep a shape is to keep the geometry.
  /// Every blade left is the blade that was there, at its size and in its place; there are simply fewer of
  /// them.
  ///
  /// <para>
  /// <c>grass2</c> is six blades of twelve triangles, so 6 / 3 / 2 blades is 72 / 36 / 24 triangles. That is a
  /// deeper ladder than the mesh's own automatic LOD offers - one level, halving to 36 - and unlike it, this
  /// one is ours to place.
  /// </para>
  ///
  /// <para>
  /// <b>For a triangle budget, more thin tufts beat fewer fat ones.</b> Dropping tufts leaves gaps, which read
  /// as the field thinning; dropping blades thins every tuft alike and leaves the field spatially even, which
  /// is what the eye reads as grass at a distance where no single blade is resolvable.
  /// </para>
  /// </remarks>
  public static class GrassBlades
  {
    /// <summary>Blades <c>grass2</c> is expected to be made of - verified at load, never assumed.</summary>
    public const int Expected = 6;

    /// <summary>
    /// The triangles of the <paramref name="keep"/> tallest blades, as indices into the same vertices.
    /// </summary>
    /// <remarks>
    /// <b>Tallest, not first.</b> <c>TerrainGrass.NaturalHeight</c> and every transform the scatter builds are
    /// calibrated against how tall this mesh stands, so dropping the tallest blade would quietly shorten the
    /// field at distance - the reader would see the far grass sink and have nothing to point at.
    ///
    /// <para>
    /// The vertices are handed back untouched and unreferenced ones simply go undrawn. Remapping them would
    /// save a few kilobytes once, at the cost of having to rebuild every other array in step.
    /// </para>
    /// </remarks>
    /// <returns>False if the mesh is not blades at all, in which case nothing is written.</returns>
    public static bool Reduce(Vector3[] vertices, int[] indices, int keep, out int[] reduced)
    {
      reduced = null;

      if (vertices == null || indices == null || indices.Length % 3 != 0 || keep <= 0)
      {
        return false;
      }

      var blade = Split(vertices, indices, out var blades);

      if (blades <= 0 || keep >= blades)
      {
        return false;
      }

      // Each blade's highest point, which is what "tallest" is measured on.
      var peak = new float[blades];

      for (var i = 0; i < peak.Length; i++)
      {
        peak[i] = float.NegativeInfinity;
      }

      for (var i = 0; i < vertices.Length; i++)
      {
        var owner = blade[i];

        if (owner >= 0 && vertices[i].Y > peak[owner])
        {
          peak[owner] = vertices[i].Y;
        }
      }

      // Selection rather than a sort: `blades` is six, and a partial pass keeps the tie-break stable on index.
      var kept = new bool[blades];

      for (var taken = 0; taken < keep; taken++)
      {
        var best = -1;

        for (var i = 0; i < blades; i++)
        {
          if (!kept[i] && (best < 0 || peak[i] > peak[best]))
          {
            best = i;
          }
        }

        kept[best] = true;
      }

      var survivors = 0;

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        if (kept[blade[indices[t]]])
        {
          survivors += 3;
        }
      }

      reduced = new int[survivors];

      var at = 0;

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        if (!kept[blade[indices[t]]])
        {
          continue;
        }

        reduced[at] = indices[t];
        reduced[at + 1] = indices[t + 1];
        reduced[at + 2] = indices[t + 2];
        at += 3;
      }

      return true;
    }

    /// <summary>Which blade each vertex belongs to. -1 for a vertex no triangle uses.</summary>
    /// <remarks>
    /// <b>Welded by position before the triangles are walked, and skipping that is a bug rather than an
    /// inefficiency.</b> Over the index buffer alone <c>grass2</c> comes apart into nine pieces and not six:
    /// its blades are cut along their own UV seams, so the two sides of a seam share a position but no index.
    /// Three blades survive whole and the rest arrive as fragments of 2, 4, 6 and 8 triangles - and one of
    /// those fragments carries the mesh's highest point, so <see cref="Reduce"/>'s "keep the tallest" would
    /// have kept half-blades.
    ///
    /// <para>
    /// Welded pairwise, because the mesh is a hundred vertices and this runs once at load: a grid or a sort
    /// would be machinery in place of a loop that finishes before it is worth measuring.
    /// </para>
    /// </remarks>
    public static int[] Split(Vector3[] vertices, int[] indices, out int blades)
    {
      blades = 0;

      var vertexCount = vertices.Length;
      var parent = new int[vertexCount];

      for (var i = 0; i < parent.Length; i++)
      {
        parent[i] = i;
      }

      // A tenth of a millimetre, squared: far below anything the art distinguishes, far above float drift.
      const float WeldSquared = 1e-8f;

      for (var i = 0; i < vertexCount; i++)
      {
        for (var j = i + 1; j < vertexCount; j++)
        {
          if (vertices[i].DistanceSquaredTo(vertices[j]) <= WeldSquared)
          {
            Union(parent, i, j);
          }
        }
      }

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        Union(parent, indices[t], indices[t + 1]);
        Union(parent, indices[t], indices[t + 2]);
      }

      var label = new int[vertexCount];
      var owner = new int[vertexCount];

      for (var i = 0; i < label.Length; i++)
      {
        label[i] = -1;
        owner[i] = -1;
      }

      // Only vertices a triangle actually reaches get a blade, so a stray unreferenced vertex cannot invent one.
      for (var t = 0; t < indices.Length; t++)
      {
        var root = Find(parent, indices[t]);

        if (owner[root] < 0)
        {
          owner[root] = blades++;
        }

        label[indices[t]] = owner[root];
      }

      return label;
    }

    private static int Find(int[] parent, int i)
    {
      while (parent[i] != i)
      {
        parent[i] = parent[parent[i]];
        i = parent[i];
      }

      return i;
    }

    private static void Union(int[] parent, int a, int b)
    {
      var rootA = Find(parent, a);
      var rootB = Find(parent, b);

      if (rootA != rootB)
      {
        parent[rootB] = rootA;
      }
    }

    /// <summary>
    /// <paramref name="source"/> with all but its <paramref name="keep"/> tallest blades taken out.
    /// </summary>
    /// <remarks>
    /// The surface's own arrays are reused wholesale and only the index buffer is replaced, so tangents, UVs
    /// and normals carry over exactly and the reduced tuft is shaded identically to the full one.
    /// </remarks>
    /// <returns>Null if the mesh could not be split, so the caller keeps drawing the full tuft.</returns>
    public static ArrayMesh Thin(Godot.Mesh source, int keep)
    {
      if (source == null || source.GetSurfaceCount() < 1)
      {
        return null;
      }

      var arrays = source.SurfaceGetArrays(0);

      if (arrays.Count <= (int)Godot.Mesh.ArrayType.Index)
      {
        return null;
      }

      var vertices = arrays[(int)Godot.Mesh.ArrayType.Vertex].AsVector3Array();
      var indices = arrays[(int)Godot.Mesh.ArrayType.Index].AsInt32Array();

      if (!Reduce(vertices, indices, keep, out var reduced))
      {
        return null;
      }

      arrays[(int)Godot.Mesh.ArrayType.Index] = reduced;

      var thinned = new ArrayMesh();
      thinned.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays);

      return thinned;
    }
  }
}
