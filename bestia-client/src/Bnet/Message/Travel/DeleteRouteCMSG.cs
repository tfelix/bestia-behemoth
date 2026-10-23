using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  public partial class DeleteRouteCMSG : ICMSG
  {
    [Export] public ulong RouteId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { DeleteRoute = new global::Bnet.DeleteRouteCMSG { RouteId = RouteId } };
    }
  }
}
