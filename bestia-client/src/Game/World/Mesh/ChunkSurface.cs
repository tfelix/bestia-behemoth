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

  /// <summary>Both surfaces of one chunk, plus what the renderer needs to know about staleness.</summary>
  public sealed class ChunkMesh
  {
    public ChunkKey Key { get; init; }

    /// <summary>Opaque terrain. Null when the chunk has no solid surface in it.</summary>
    public ChunkSurface Terrain { get; init; }

    /// <summary>Water and anything else the palette calls non-solid. Null when the chunk is dry.</summary>
    public ChunkSurface Water { get; init; }

    /// <summary>
    /// Chunks whose absence was papered over by extending a boundary, so this mesh is provisional.
    /// </summary>
    /// <remarks>
    /// The renderer keeps these so that when one of them decodes it knows to re-mesh, rather than having to
    /// re-mesh a chunk's whole neighbourhood on every arrival.
    /// </remarks>
    public ChunkKey[] MissingNeighbours { get; init; }

    public bool IsEmpty => (Terrain == null || Terrain.IsEmpty) && (Water == null || Water.IsEmpty);
  }
}
