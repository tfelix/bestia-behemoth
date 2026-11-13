using Bnet;

namespace BestiaBehemothClient.Bnet.Message.SpellBinding
{
  /// <summary>Asks for the master's own Spell Bindings.</summary>
  public partial class SpellBindingsCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { SpellBindingsRequest = new global::Bnet.SpellBindingsCMSG() };
    }
  }
}
