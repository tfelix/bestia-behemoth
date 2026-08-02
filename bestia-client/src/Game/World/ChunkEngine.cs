namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The one version number this client has to agree with the server about to read a chunk at all.
  /// </summary>
  /// <remarks>
  /// It covers the two things the client does with a chunk: decode it (<see cref="RleCodec"/>) and name the
  /// materials in it (<see cref="Mesh.BlockAppearance.Palette"/>). Either changing makes every payload wrong,
  /// and neither is something the client can adapt to at runtime, so there is nothing to gain from knowing
  /// which one moved.
  ///
  /// <para>
  /// The server has a finer-grained version vector - pipeline, palette, format - but keeps it to itself: it
  /// needs the distinction to decide what to invalidate in a cache, and a client that only ever receives
  /// merged chunks has no such decision to make.
  /// </para>
  ///
  /// <para>
  /// Must equal <c>ChunkEngine.VERSION</c> in <c>worldgen</c>. The two move in the same commit; the server's
  /// <c>ChunkStoreTest</c> pins the palette hash so that changing <c>BlockType</c> without coming here fails
  /// the build.
  /// </para>
  /// </remarks>
  public static class ChunkEngine
  {
    // 2: the seven worked materials buildings and streets are made of - TIMBER through COBBLESTONE.
    public const uint Version = 3;
  }
}
