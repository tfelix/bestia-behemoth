using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  /// <summary>Keeps a route under a name; an empty one lets the server name it from where it starts and ends.</summary>
  public partial class SaveRouteCMSG : ICMSG
  {
    [Export] public Godot.Collections.Array<Vector3> Waypoints { get; set; } = new Godot.Collections.Array<Vector3>();

    [Export] public string Name { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      var save = new global::Bnet.SaveRouteCMSG { Name = Name };
      foreach (var waypoint in Waypoints)
      {
        save.Waypoints.Add(Vec3Convert.ToProto(waypoint));
      }

      return new Envelope { SaveRoute = save };
    }
  }
}
