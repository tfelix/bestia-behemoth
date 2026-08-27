using Bnet;

namespace BestiaBehemothClient.Bnet.Message.System
{
  /// <summary>
  /// Asks the server to revive the currently active entity at its save point. Carries nothing: which
  /// entity acts comes from the session and where it belongs from the server's own records.
  /// </summary>
  public partial class RespawnCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        Respawn = new global::Bnet.RespawnCMSG()
      };
    }
  }
}
