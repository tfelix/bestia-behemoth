using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Asks for the diary of the selected master's bestias.</summary>
  public partial class GetBestiaDiaryCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { GetBestiaDiary = new global::Bnet.GetBestiaDiaryCMSG() };
    }
  }
}
