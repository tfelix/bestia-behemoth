using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  public partial class RequestSavedRoutesCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { RequestSavedRoutes = new global::Bnet.RequestSavedRoutesCMSG() };
    }
  }
}
