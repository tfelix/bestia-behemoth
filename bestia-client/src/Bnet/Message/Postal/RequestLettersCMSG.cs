using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Asks for the played master's mailbox.</summary>
  public partial class RequestLettersCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { RequestLetters = new global::Bnet.RequestLettersCMSG() };
    }
  }
}
