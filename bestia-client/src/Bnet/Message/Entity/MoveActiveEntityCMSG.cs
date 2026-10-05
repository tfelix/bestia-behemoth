using System.Collections.Generic;
using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message to move the account's currently active entity along a path.
  /// An empty path is a stop request.
  /// </summary>
  public partial class MoveActiveEntityCMSG : ICMSG
  {
    [Export] public Godot.Collections.Array<Vector3> Path { get; set; } = new Godot.Collections.Array<Vector3>();

    /// <summary>Adds the path to the walk under way instead of replacing it.</summary>
    [Export] public bool Append { get; set; }

    public MoveActiveEntityCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var steps = new List<global::Bnet.Vec3>();
      foreach (var point in Path)
      {
        steps.Add(Vec3Convert.ToProto(point));
      }

      var moveActiveEntity = new global::Bnet.MoveActiveEntity
      {
        Path = DeltaPathConvert.Encode(steps),
        Append = Append
      };

      return new Envelope
      {
        MoveActiveEntity = moveActiveEntity
      };
    }
  }
}
