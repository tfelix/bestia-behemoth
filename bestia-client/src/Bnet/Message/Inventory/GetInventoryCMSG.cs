using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class GetInventoryCMSG : ICMSG
  {
    public ulong EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      var getInventory = new global::Bnet.GetInventoryCMSG
      {
        EntityId = EntityId
      };

      return new Envelope
      {
        GetInventory = getInventory
      };
    }
  }
}
