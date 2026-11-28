using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Every open war zone, where the Orders fight without honor loss. Sent whole on each change; empty when none is open.
  /// </summary>
  [GlobalClass]
  public partial class WarZonesSMSG : ISMSG
  {
    /// <summary>Centre x, centre y and radius in metres, like a treasure map's circle.</summary>
    [Export] public Godot.Collections.Array<Vector3> Zones { get; set; } = [];

    public static WarZonesSMSG FromProto(global::Bnet.WarZonesSMSG proto)
    {
      var message = new WarZonesSMSG();
      foreach (var zone in proto.Zones)
      {
        message.Zones.Add(new Vector3((float)zone.X, (float)zone.Y, (float)zone.Radius));
      }

      return message;
    }
  }
}
