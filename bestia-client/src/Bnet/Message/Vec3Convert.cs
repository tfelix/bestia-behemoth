using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Converts a Godot (Y-up) tile coordinate into the server's Vec3 (Z-up,
  /// whole tile coordinates only). Mirrors, in reverse, the mapping used by
  /// PathComponentSMSG.FromProto (new Vector3(proto.X, proto.Z, proto.Y)).
  /// </summary>
  /// <remarks>
  /// Takes a <b>tile coordinate</b>, not a world position, and the difference is not academic. Rounding is right
  /// for a tile index that is fractional only because an entity is mid-step: the tile it is on is the nearest
  /// one. A world position off a raycast belongs to the cell containing it, which is the one below - a cell spans
  /// <c>[n, n+1]</c>. So floor a world position into a tile first (<c>TileSpace.world_to_tile</c>) and hand the
  /// result to this; passing the raw hit puts half of every tile onto its neighbour.
  /// </remarks>
  public static class Vec3Convert
  {
    public static global::Bnet.Vec3 ToProto(Vector3 position)
    {
      return new global::Bnet.Vec3
      {
        X = (long)Mathf.Round(position.X),
        Y = (long)Mathf.Round(position.Z),
        Z = (long)Mathf.Round(position.Y)
      };
    }
  }
}
