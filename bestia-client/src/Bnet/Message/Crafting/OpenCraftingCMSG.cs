using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Open the crafting window where the player stands: at a station in range, or as Handiwork. The answer is
  /// a <see cref="CraftableRecipesSMSG"/>.
  /// </summary>
  public partial class OpenCraftingCMSG : ICMSG
  {
    /// <summary>The skill to show first, which also picks its station when one is in range. 0 picks none.</summary>
    public ulong SkillId { get; set; }

    /// <summary>The station the window is already open at, so a refresh stays there; 0 lets the server pick.</summary>
    public ulong StructureId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        OpenCrafting = new global::Bnet.OpenCraftingCMSG { SkillId = SkillId, StructureId = StructureId }
      };
    }
  }
}
