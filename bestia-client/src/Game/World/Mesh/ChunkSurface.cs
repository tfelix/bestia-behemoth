using Godot;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// One surface of one chunk's mesh, as the plain arrays <c>ArrayMesh.AddSurfaceFromArrays</c> wants.
  /// </summary>
  /// <remarks>
  /// Deliberately holds no Godot resources - only <see cref="Vector3"/> and <see cref="Color"/>, which are value
  /// types and safe anywhere. That is what lets the whole mesher run on a worker thread: the main thread's share
  /// of the work is the <c>ArrayMesh</c> and the upload, and nothing else.
  /// </remarks>
  public sealed class ChunkSurface
  {
    public Vector3[] Vertices { get; init; }
    public Vector3[] Normals { get; init; }
    public Color[] Colours { get; init; }
    public int[] Indices { get; init; }

    public int TriangleCount => Indices.Length / 3;

    public bool IsEmpty => Indices.Length == 0;
  }

  /// <summary>Every surface of one chunk, plus what the renderer needs to know about staleness.</summary>
  public sealed class ChunkMesh
  {
    public ChunkKey Key { get; init; }

    /// <summary>
    /// One entry per <see cref="BlockAppearance.SurfaceKind"/>, null where the chunk has none of that surface.
    /// </summary>
    /// <remarks>
    /// Indexed by kind rather than held as a named field per kind, so adding a surface does not mean touching
    /// this class, the mesher's return, and the renderer's per-tile nodes. The named accessors below are kept
    /// because most readers want one specific surface and <c>mesh.Water</c> says more than <c>mesh[1]</c>.
    /// </remarks>
    public ChunkSurface[] Surfaces { get; init; } = new ChunkSurface[BlockAppearance.SurfaceKinds];

    /// <summary>Opaque terrain. Null when the chunk has no solid surface in it.</summary>
    public ChunkSurface Terrain => Surfaces[(int)BlockAppearance.SurfaceKind.Terrain];

    /// <summary>Transparent water. Null when the chunk is dry.</summary>
    public ChunkSurface Water => Surfaces[(int)BlockAppearance.SurfaceKind.Water];

    /// <summary>Opaque, emissive lava. Null when there is none in the chunk, which is nearly always.</summary>
    public ChunkSurface Lava => Surfaces[(int)BlockAppearance.SurfaceKind.Lava];

    /// <summary>
    /// Chunks whose absence was papered over by extending a boundary, so this mesh is provisional.
    /// </summary>
    /// <remarks>
    /// The renderer keeps these so that when one of them decodes it knows to re-mesh, rather than having to
    /// re-mesh a chunk's whole neighbourhood on every arrival.
    /// </remarks>
    public ChunkKey[] MissingNeighbours { get; init; }

    public bool IsEmpty
    {
      get
      {
        foreach (var surface in Surfaces)
        {
          if (surface != null && !surface.IsEmpty)
          {
            return false;
          }
        }

        return true;
      }
    }
  }
}
