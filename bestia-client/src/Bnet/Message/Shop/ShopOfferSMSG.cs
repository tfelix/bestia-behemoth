using Godot;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>
  /// What one merchant will trade right now, and at what price.
  /// </summary>
  /// <remarks>
  /// A snapshot for drawing a window, never a contract: every trade is settled unit by unit against the
  /// live price when it resolves. Re-sent after every trade, which is how the window stays honest - so
  /// the panel re-renders wholesale on each one rather than patching the row it just changed.
  /// </remarks>
  [GlobalClass]
  public partial class ShopOfferSMSG : ISMSG
  {
    /// <summary>Dense settlement index, so one town's window can be told from another's.</summary>
    [Export] public int Settlement { get; set; }

    /// <summary>
    /// Whose counter this is. Carried rather than remembered, because the window is not always something
    /// this client asked for - asking a merchant for their wares in conversation pushes one.
    /// </summary>
    [Export] public ulong MerchantEntityId { get; set; }

    [Export] public string MerchantName { get; set; } = "";

    /// <summary>False for a merchant who keeps no hours; the two minutes below are then meaningless.</summary>
    [Export] public bool KeepsHours { get; set; }

    /// <summary>The merchant's own minutes of the day, already moved by how early or late they work.</summary>
    [Export] public int OpensAtMinute { get; set; }

    [Export] public int ClosesAtMinute { get; set; }

    [Export] public Godot.Collections.Array<ShopEntry> Entries { get; set; } = [];

    public static ShopOfferSMSG FromProto(global::Bnet.ShopOfferSMSG proto)
    {
      var entries = new Godot.Collections.Array<ShopEntry>();
      foreach (var entry in proto.Entries)
      {
        entries.Add(ShopEntry.FromProto(entry));
      }

      return new ShopOfferSMSG
      {
        Settlement = proto.Settlement,
        MerchantEntityId = proto.MerchantEntityId,
        MerchantName = proto.MerchantName,
        KeepsHours = proto.Hours != null,
        OpensAtMinute = proto.Hours?.OpensAtMinute ?? 0,
        ClosesAtMinute = proto.Hours?.ClosesAtMinute ?? 0,
        Entries = entries
      };
    }
  }
}
