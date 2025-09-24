using Bnet;

namespace BestiaBehemothClient.Bnet.Message
{
  public partial class Ping : ICMSG
  {

    public override Envelope ToEnvelope()
    {
      var ping = new global::Bnet.Ping();

      return new Envelope
      {
        Ping = ping
      };
    }
  }
}