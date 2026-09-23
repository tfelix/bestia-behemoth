using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>
  /// Asks what one merchant has on their shelves. Answered by a ShopOfferSMSG, or by an OperationError
  /// when there is no town underfoot and when the person asked keeps no shop.
  /// </summary>
  /// <remarks>
  /// The merchant chooses whose counter, never whose prices - those come from where we are standing, and
  /// the server re-checks that the merchant is close enough to speak to.
  /// </remarks>
  public partial class OpenShopCMSG : ICMSG
  {
    public ulong MerchantEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        OpenShop = new global::Bnet.OpenShopCMSG
        {
          MerchantEntityId = MerchantEntityId
        }
      };
    }
  }
}
