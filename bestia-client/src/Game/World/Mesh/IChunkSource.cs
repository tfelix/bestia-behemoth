namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// The read side of the chunk store, as the mesher needs it.
  /// </summary>
  /// <remarks>
  /// An interface rather than a direct dependency on <see cref="ClientChunkStore"/> for two reasons. It keeps the
  /// mesher testable without a store or a socket, and it makes the read-only contract explicit: meshing runs on a
  /// worker thread against chunks the network thread may still be adding to, so it must not be able to mutate
  /// anything.
  /// </remarks>
  public interface IChunkSource
  {
    /// <summary>The chunk at this position, or <c>null</c> if it is not held.</summary>
    VoxelChunk Get(ChunkKey key);

    /// <summary>The cached band scan for this position, or <c>null</c> if the chunk is not held.</summary>
    ChunkBands BandsOf(ChunkKey key);
  }
}
