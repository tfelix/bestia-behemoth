using Bnet;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>Asks which attacks Spell Inscription could copy off the master's bestias.</summary>
  public partial class InscriptionOptionsCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { InscriptionOptionsRequest = new global::Bnet.InscriptionOptionsCMSG() };
    }
  }
}
