using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The enchantments on an item lying on the ground. The item glows while it has any, and the one that ends first
  /// draws a countdown ring. Ends with <see cref="Removed"/> when the last one is broken.
  /// </summary>
  [GlobalClass]
  public partial class ItemEnchantmentsComponentSMSG : EntitySMSG
  {
    /// <summary>Ids in item_enchantments.yml.</summary>
    [Export] public Godot.Collections.Array<int> EnchantmentIds { get; set; } = [];

    /// <summary>For the one at the same index; 0 when it has no end.</summary>
    [Export] public Godot.Collections.Array<float> RemainingSeconds { get; set; } = [];

    [Export] public Godot.Collections.Array<float> TotalSeconds { get; set; } = [];

    [Export] public bool Removed { get; set; }

    public static ItemEnchantmentsComponentSMSG FromProto(ulong entityId, global::Bnet.ItemEnchantmentsComponentSMSG proto)
    {
      var message = new ItemEnchantmentsComponentSMSG { EntityId = entityId, Removed = proto.Removed };

      foreach (var enchantment in proto.Enchantments)
      {
        message.EnchantmentIds.Add((int)enchantment.EnchantmentId);
        message.RemainingSeconds.Add(enchantment.RemainingSeconds);
        message.TotalSeconds.Add(enchantment.TotalSeconds);
      }

      return message;
    }

    public override string ToString()
    {
      return $"ItemEnchantmentsComponentSMSG(EntityId={EntityId}, Count={EnchantmentIds.Count}, Removed={Removed})";
    }
  }
}
