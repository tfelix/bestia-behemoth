using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Converts a Godot (Y-up) world position into the server's Vec3 (Z-up,
  /// whole tile coordinates only). Mirrors, in reverse, the mapping used by
  /// PathComponentSMSG.FromProto (new Vector3(proto.X, proto.Z, proto.Y)).
  /// </summary>
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
