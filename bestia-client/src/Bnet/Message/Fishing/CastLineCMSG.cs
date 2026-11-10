using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Fishing
{
  /// <summary>Casts the active master's fishing line onto the water at a tile.</summary>
  public partial class CastLineCMSG : ICMSG
  {
    /// <summary>The water tile in Godot's axes, its height the water's top.</summary>
    [Export] public Vector3 Target { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CastLine = new global::Bnet.CastLineCMSG { Target = Vec3Convert.ToProto(Target) }
      };
    }
  }
}
