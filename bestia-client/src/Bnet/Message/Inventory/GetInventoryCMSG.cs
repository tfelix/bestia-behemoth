using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class GetInventoryCMSG : ICMSG
  {

    public override Envelope ToEnvelope()
    {
      var getInventory = new global::Bnet.GetInventoryCMSG();

      return new Envelope
      {
        GetInventory = getInventory
      };
    }
  }
}

