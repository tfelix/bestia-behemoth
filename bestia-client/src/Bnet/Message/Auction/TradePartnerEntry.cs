using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>An owner the house's owner has a Trade Agreement with.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file.</remarks>
  [GlobalClass]
  public partial class TradePartnerEntry : GodotObject
  {
    [Export] public ulong MasterId { get; set; }
    [Export] public string Name { get; set; } = "";
  }
}
