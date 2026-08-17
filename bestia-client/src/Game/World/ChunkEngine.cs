namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The one version number this client has to agree with the server about to read a chunk at all.
  /// </summary>
  /// <remarks>
  /// It covers the three things the client does with a chunk: decode it (<see cref="RleCodec"/>), name the
  /// materials in it (<see cref="Mesh.BlockAppearance.Palette"/>), and apply the removals that arrive
  /// afterwards (<see cref="ChunkPatchCodec"/>). Any of them changing makes payloads wrong, and none is
  /// something the client can adapt to at runtime, so there is nothing to gain from knowing which one moved.
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
    // Reset to 1 with the server's, once, when worldgen's feature work landed and before any client shipped.
    // The bumps it had accumulated were compatibility statements to a counterparty that did not exist yet.
    // Reached 6 that way - volcanic materials, the removal-only patch format, trees/crystals leaving the
    // palette for props, DRY_GRASS - each documented in a comment above this field at the time. Reset to 1,
    // for the same reason: still pre-release, still no counterparty for the promise. Append-only from the
    // first client release onwards; the git history holds the old changelog.
    //
    // 2: the palette cleanup. Building materials left for props, the four sedimentary rocks became STONE plus
    // LIMESTONE, peat and clay became MUD, four gems arrived, and every id was renumbered densely - the ids,
    // the names and the row count all at once, which is every clause of the bump rule in one change.
    public const uint Version = 2;

    /// <summary>
    /// Voxels per chunk edge, for expanding a chunk-local coordinate to a global one.
    /// </summary>
    /// <remarks>
    /// Unlike <see cref="Version"/> this is not a build-time compatibility constant - it is per-world runtime
    /// config carried on <c>WorldInfoSMSG</c>, set once by <see cref="ChunkStreamManager"/> the instant a
    /// connection authenticates (before any chunk or static-entity batch can arrive) and never touched again
    /// until the next <c>WorldInfoSMSG</c>, which is exactly the point every prior chunk is discarded too.
    /// Held here rather than threaded as a parameter because <c>ChunkStaticEntitiesSMSG.FromProto</c> runs
    /// inside <c>BnetSocket</c>'s stateless decode dispatch, which has no world context of its own.
    /// </remarks>
    public static int ChunkSize { get; set; }
  }
}
