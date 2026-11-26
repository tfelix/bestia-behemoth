using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// An enchantment on an item in the bag or a slot. The server sends the time left; it is kept as a deadline
  /// on the local clock, so a tooltip written later still says how long is left.
  /// </summary>
  [GlobalClass]
  public partial class HeldItemEnchantment : RefCounted
  {
    /// <summary>The id in the server's <c>item_enchantments.yml</c>.</summary>
    [Export] public uint EnchantmentId { get; set; }

    /// <summary><c>Time.GetTicksMsec()</c> at its end; 0 for an enchantment without an end.</summary>
    [Export] public ulong EndsAtMsec { get; set; }

    /// <summary>The element an endow lends the weapon, like <c>"FIRE"</c>; empty for anything else.</summary>
    [Export] public string Element { get; set; } = "";

    public static HeldItemEnchantment FromProto(global::Bnet.HeldItemEnchantment proto)
    {
      var endsAtMsec = proto.RemainingSeconds > 0
        ? Time.GetTicksMsec() + (ulong)(proto.RemainingSeconds * 1000)
        : 0;

      return new HeldItemEnchantment
      {
        EnchantmentId = proto.EnchantmentId, EndsAtMsec = endsAtMsec, Element = proto.Element
      };
    }

    public static Godot.Collections.Array<HeldItemEnchantment> FromProto(
      Google.Protobuf.Collections.RepeatedField<global::Bnet.HeldItemEnchantment> protoEnchantments)
    {
      var enchantments = new Godot.Collections.Array<HeldItemEnchantment>();
      foreach (var protoEnchantment in protoEnchantments)
      {
        enchantments.Add(FromProto(protoEnchantment));
      }

      return enchantments;
    }
  }
}
