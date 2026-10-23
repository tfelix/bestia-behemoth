using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  /// <summary>An empty name gives the route its server-made name back.</summary>
  public partial class RenameRouteCMSG : ICMSG
  {
    [Export] public ulong RouteId { get; set; }

    [Export] public string Name { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope { RenameRoute = new global::Bnet.RenameRouteCMSG { RouteId = RouteId, Name = Name } };
    }
  }
}
