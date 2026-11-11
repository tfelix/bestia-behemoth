using Bnet;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>Asks which attack each of these spell scrolls holds.</summary>
  public partial class SpellScrollsCMSG : ICMSG
  {
    public Godot.Collections.Array<long> UniqueIds { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var request = new global::Bnet.SpellScrollsCMSG();
      foreach (var uniqueId in UniqueIds)
      {
        request.UniqueIds.Add((ulong)uniqueId);
      }

      return new Envelope { SpellScrollsRequest = request };
    }
  }
}
