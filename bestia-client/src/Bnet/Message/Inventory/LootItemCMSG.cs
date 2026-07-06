using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class LootItemCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      var lootItem = new global::Bnet.LootItemCMSG
      {
        EntityId = EntityId
      };

      return new Envelope
      {
        LootItem = lootItem
      };
    }
  }
}
