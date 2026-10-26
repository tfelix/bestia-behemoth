using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Try a mix of held piles against the blueprints of one crafting skill. The materials are always spent; the
  /// answer is a <see cref="DiscoveryResultSMSG"/>.
  /// </summary>
  public partial class DiscoverRecipeCMSG : ICMSG
  {
    public ulong SkillId { get; set; }

    /// <summary>The piles, as InventoryItem.PlayerItemId names them, side by side with <see cref="Amounts"/>.</summary>
    public long[] PlayerItemIds { get; set; } = [];
    public int[] Amounts { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var discover = new global::Bnet.DiscoverRecipeCMSG { SkillId = SkillId };
      for (var i = 0; i < PlayerItemIds.Length; i++)
      {
        discover.Materials.Add(new DiscoveryMaterial
        {
          PlayerItemId = (ulong)PlayerItemIds[i],
          Amount = (uint)Amounts[i]
        });
      }

      return new Envelope { DiscoverRecipe = discover };
    }
  }
}
