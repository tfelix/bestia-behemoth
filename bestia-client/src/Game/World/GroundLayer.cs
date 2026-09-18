namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// A kind of lasting mark on the ground, and the channel it composites through.
  /// </summary>
  /// <remarks>
  /// The mirror of the server's <c>GroundLayer</c>. Both numbers are wire contracts and neither is checkable
  /// at runtime - any byte string is a legal payload, so a disagreement paints the world wrong rather than
  /// throwing. <c>GroundLayerTest</c> pins them on both sides.
  ///
  /// <para>
  /// A mark cannot be a <c>BlockType</c>: the chunk patch format can only ever remove a voxel, so no message
  /// in this protocol can change a voxel's material. Ground history travels beside the ground.
  /// </para>
  /// </remarks>
  public static class GroundLayers
  {
    /// <summary>
    /// How many marks may stack on one square metre, which is the channels one mark texture carries.
    /// </summary>
    /// <remarks>
    /// Four, because the mark texture is a single <c>RGBA8</c>. Unlike
    /// <see cref="Mesh.BlockAppearance.Slots"/> this is not near an engine limit - a second texture buys four
    /// more for one more fetch - so it is a budget rather than a wall.
    /// </remarks>
    public const int Channels = 4;

    /// <summary>Wire ids, as the proto's <c>GroundLayerId</c> numbers them. Never reused.</summary>
    public enum Id
    {
      Unspecified = 0,
      Scorched = 1,
      Worn = 2,
      Bloodied = 3,
      Disturbed = 4
    }

    /// <summary>
    /// Which channel a layer packs into, and the order layers composite in, lowest first.
    /// </summary>
    /// <returns>-1 for a layer this build does not know, which is skipped rather than guessed at.</returns>
    public static int ChannelOf(Id layer) => layer switch
    {
      Id.Scorched => 0,
      Id.Worn => 1,
      Id.Bloodied => 2,
      Id.Disturbed => 3,
      _ => -1
    };
  }
}
