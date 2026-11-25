using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Names one of the player's party bestias; an empty name gives it back its species' name.</summary>
  public partial class RenameBestiaCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public string Name { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope { RenameBestia = new global::Bnet.RenameBestiaCMSG { EntityId = EntityId, Name = Name } };
    }
  }
}
