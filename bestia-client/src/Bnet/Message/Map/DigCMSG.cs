using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Starts digging at a ground tile with the shovel or pickaxe in the right hand.
  /// </summary>
  public partial class DigCMSG : ICMSG
  {
    [Export] public Vector3 Position { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        Dig = new global::Bnet.DigCMSG { Position = Vec3Convert.ToProto(Position) }
      };
    }
  }
}
