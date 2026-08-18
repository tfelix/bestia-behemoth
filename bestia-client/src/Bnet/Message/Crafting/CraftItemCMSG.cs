using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Start one craft. Sent from the crafting window, which opened because the player activated a
  /// crafting skill - activation and execution are separate messages because
  /// <c>ActivateSkillCMSG</c> carries no recipe.
  ///
  /// The server may refuse (unknown recipe, skill too low, no station in range, materials short,
  /// already crafting) with an <c>OperationError</c>. Nothing is applied locally either way.
  /// </summary>
  public partial class CraftItemCMSG : ICMSG
  {
    public ulong RecipeId { get; set; }

    /// <summary>
    /// The item instance to work on, or 0 for a recipe that makes something instead.
    /// </summary>
    public ulong TargetUniqueId { get; set; }

    public override Envelope ToEnvelope()
    {
      var craftItem = new global::Bnet.CraftItemCMSG
      {
        RecipeId = RecipeId,
        TargetUniqueId = TargetUniqueId
      };

      return new Envelope
      {
        CraftItem = craftItem
      };
    }
  }
}
