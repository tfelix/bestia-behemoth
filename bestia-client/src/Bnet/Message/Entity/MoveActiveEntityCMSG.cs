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

    public MoveActiveEntityCMSG()
    {
    }

    public override Envelope ToEnvelope()
    {
      var moveActiveEntity = new global::Bnet.MoveActiveEntity();
      foreach (var point in Path)
      {
        moveActiveEntity.Path.Add(Vec3Convert.ToProto(point));
      }

      return new Envelope
      {
        MoveActiveEntity = moveActiveEntity
      };
    }
  }
}
