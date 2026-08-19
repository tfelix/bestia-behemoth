using Godot;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// How far a trade has got. Ordinals match <c>bnet.TradeStatus</c>.
  /// </summary>
  public enum TradeStatus
  {
    Open = 0,
    Locked = 1,
    Completed = 2,
    Cancelled = 3
  }

  /// <summary>
  /// The whole trade, as this client sees it. One message opens the window, updates it and closes it.
  ///
  /// Always a full snapshot, so the window can re-render both columns wholesale on every one. That is also
  /// how a refused action corrects itself: the server re-sends this and the optimistic client snaps back.
  /// </summary>
  [GlobalClass]
  public partial class TradeStateSMSG : ISMSG
  {
    [Export] public ulong TradeId { get; set; }

    [Export] public TradeStatus Status { get; set; } = TradeStatus.Open;

    /// <summary>
    /// <see cref="Status"/> as a lowercase string, for the GDScript side - it cannot see a C# enum's
    /// members, so matching on this avoids re-declaring the ordinals over there by hand.
    /// </summary>
    [Export] public string StatusName { get; set; } = "open";

    [Export] public string PartnerMasterName { get; set; } = string.Empty;

    [Export] public ulong PartnerEntityId { get; set; }

    [Export] public Godot.Collections.Array<TradeOfferItem> OwnOffer { get; set; } = [];

    [Export] public Godot.Collections.Array<TradeOfferItem> PartnerOffer { get; set; } = [];

    /// <summary>Reserved for currency, which does not exist yet - always 0.</summary>
    [Export] public ulong OwnGold { get; set; }

    [Export] public ulong PartnerGold { get; set; }

    [Export] public bool OwnLocked { get; set; }

    [Export] public bool PartnerLocked { get; set; }

    [Export] public bool OwnConfirmed { get; set; }

    [Export] public bool PartnerConfirmed { get; set; }

    public static TradeStateSMSG FromProto(global::Bnet.TradeStateSMSG proto)
    {
      var own = new Godot.Collections.Array<TradeOfferItem>();
      foreach (var item in proto.OwnOffer)
      {
        own.Add(TradeOfferItem.FromProto(item));
      }

      var partner = new Godot.Collections.Array<TradeOfferItem>();
      foreach (var item in proto.PartnerOffer)
      {
        partner.Add(TradeOfferItem.FromProto(item));
      }

      var status = MapStatus(proto.Status);

      return new TradeStateSMSG
      {
        TradeId = proto.TradeId,
        Status = status,
        StatusName = status.ToString().ToLowerInvariant(),
        PartnerMasterName = proto.PartnerMasterName,
        PartnerEntityId = proto.PartnerEntityId,
        OwnOffer = own,
        PartnerOffer = partner,
        OwnGold = proto.OwnGold,
        PartnerGold = proto.PartnerGold,
        OwnLocked = proto.OwnLocked,
        PartnerLocked = proto.PartnerLocked,
        OwnConfirmed = proto.OwnConfirmed,
        PartnerConfirmed = proto.PartnerConfirmed
      };
    }

    private static TradeStatus MapStatus(global::Bnet.TradeStatus status)
    {
      return status switch
      {
        global::Bnet.TradeStatus.Locked => TradeStatus.Locked,
        global::Bnet.TradeStatus.Completed => TradeStatus.Completed,
        global::Bnet.TradeStatus.Cancelled => TradeStatus.Cancelled,
        _ => TradeStatus.Open
      };
    }
  }
}
