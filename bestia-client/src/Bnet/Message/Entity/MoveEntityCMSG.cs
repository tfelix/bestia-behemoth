using System.Collections.Generic;
using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Moves one of the player's own entities through waypoints; the server plans the way. No waypoints stops it.
  /// </summary>
  public partial class MoveEntityCMSG : ICMSG
  {
    [Export] public ulong EntityId { get; set; }

    [Export] public Godot.Collections.Array<Vector3> Waypoints { get; set; } = new Godot.Collections.Array<Vector3>();

    /// <summary>Adds the waypoints after the destination of the journey under way.</summary>
    [Export] public bool Append { get; set; }

    /// <summary>The tile steps this client already walks to the first waypoint; walked as sent when valid.</summary>
    [Export] public Godot.Collections.Array<Vector3> FirstLeg { get; set; } = new Godot.Collections.Array<Vector3>();

    public override Envelope ToEnvelope()
    {
      var move = new global::Bnet.MoveEntity
      {
        EntityId = EntityId,
        Append = Append
      };

      foreach (var waypoint in Waypoints)
      {
        move.Waypoints.Add(Vec3Convert.ToProto(waypoint));
      }

      if (FirstLeg.Count > 0)
      {
        var steps = new List<global::Bnet.Vec3>();
        foreach (var step in FirstLeg)
        {
          steps.Add(Vec3Convert.ToProto(step));
        }

        move.FirstLeg = DeltaPathConvert.Encode(steps);
      }

      return new Envelope
      {
        MoveEntity = move
      };
    }
  }
}
