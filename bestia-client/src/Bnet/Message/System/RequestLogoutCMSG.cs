using Bnet;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Asks the server to begin the (cancellable, settlement-protected) logout countdown for the
  /// currently active master. Progress, cancellation and completion are driven by the server.
  /// </summary>
  public partial class RequestLogoutCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestLogout = new global::Bnet.RequestLogoutCMSG()
      };
    }
  }
}
