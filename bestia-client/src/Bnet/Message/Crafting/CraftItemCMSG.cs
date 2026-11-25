using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Start a craft, or a batch of the same craft. Sent from the crafting window - opening it and crafting
  /// are separate messages, because a window that lists nine recipes cannot say which one is wanted.
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

    /// <summary>How many to make one after another. Only a producing recipe repeats.</summary>
    public uint Amount { get; set; } = 1;

    public override Envelope ToEnvelope()
    {
      var craftItem = new global::Bnet.CraftItemCMSG
      {
        RecipeId = RecipeId,
        TargetUniqueId = TargetUniqueId,
        Amount = Amount
      };

      return new Envelope
      {
        CraftItem = craftItem
      };
    }
  }
}
