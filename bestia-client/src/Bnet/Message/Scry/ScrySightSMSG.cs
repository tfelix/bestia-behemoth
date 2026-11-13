using Godot;

namespace BestiaBehemothClient.Bnet.Message.Scry
{
  /// <summary>
  /// A Sage's Scry opened at <see cref="Centre"/>, or ended when <see cref="Open"/> is false. The centre is in Godot
  /// world coordinates, converted like an entity position.
  /// </summary>
  [GlobalClass]
  public partial class ScrySightSMSG : ISMSG
  {
    [Export] public bool Open { get; set; }

    [Export] public Vector3 Centre { get; set; } = Vector3.Zero;

    [Export] public float RadiusMetres { get; set; }

    [Export] public float Seconds { get; set; }

    public static ScrySightSMSG FromProto(global::Bnet.ScrySightSMSG proto)
    {
      return new ScrySightSMSG
      {
        Open = proto.Open,
        Centre = new Vector3(proto.X, proto.Z, proto.Y),
        RadiusMetres = proto.RadiusMetres,
        Seconds = proto.Seconds
      };
    }
  }
}
