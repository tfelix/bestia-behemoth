using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Compendium
{
  /// <summary>Asks for the selected master's Bestia Compendium; answered with a <see cref="CompendiumSMSG"/>.</summary>
  public partial class RequestCompendiumCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestCompendium = new global::Bnet.RequestCompendiumCMSG()
      };
    }
  }
}
