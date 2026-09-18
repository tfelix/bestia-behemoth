namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The nibble layout one ground layer's cells arrive in.
  /// </summary>
  /// <remarks>
  /// <b>This is half of a wire contract</b>, and the half that cannot fail loudly: any byte string is a legal
  /// payload, so a decoder that disagrees about the order draws plausible wear in the wrong places rather than
  /// throwing. <c>ColumnLevelsTest</c> pins the same layout on the server and
  /// <c>GroundLayerCellsTest</c> pins it here, both from hand-written bytes rather than from each other.
  ///
  /// <para>
  /// Godot-free on purpose, so the layout can be tested without an engine - which is the only reason there is
  /// a test of it at all. <see cref="Bnet.Message.Map.ChunkGroundLayersSMSG"/> and
  /// <see cref="GroundMarkTexture"/> both read through here rather than each unpacking their own nibbles.
  /// </para>
  /// </remarks>
  public static class GroundLayerCells
  {
    /// <summary>The strongest level a cell can carry, being all four bits.</summary>
    public const int MaxLevel = 15;

    /// <summary>How many bytes a column's cells occupy: two cells to a byte.</summary>
    public static int ByteLength(int chunkSize) => (chunkSize * chunkSize + 1) / 2;

    /// <summary>
    /// How strongly this layer marks cell <c>(localX, localY)</c>, 0 to <see cref="MaxLevel"/>.
    /// </summary>
    /// <remarks>
    /// <c>localY * chunkSize + localX</c>, that index's nibble in byte <c>index / 2</c> - low nibble for an
    /// even index, high for an odd one. The same cell order <c>ColumnMask</c> and <c>ColumnSummary</c> use, so
    /// a reader who knows one knows the others.
    /// </remarks>
    /// <returns>0 for absent cells, which is what a layer this column has nothing of looks like.</returns>
    public static int LevelAt(byte[] cells, int chunkSize, int localX, int localY)
    {
      if (cells == null)
      {
        return 0;
      }

      var index = localY * chunkSize + localX;
      var packed = cells[index >> 1];

      return (index & 1) == 0 ? packed & 0x0F : packed >> 4 & 0x0F;
    }

    /// <summary>The same level as the 0..1 a shader wants.</summary>
    public static float UnitAt(byte[] cells, int chunkSize, int localX, int localY) =>
      LevelAt(cells, chunkSize, localX, localY) / (float)MaxLevel;
  }
}
