using System.Collections.Generic;
using System.IO;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The server's settings for the marks this client lays itself, as <c>GroundMarkRulesSMSG</c> states them.
  /// </summary>
  /// <remarks>
  /// Nothing derives a footprint, wear or blood until this exists: a client guessing at the numbers would draw
  /// plausible marks that disagree with the server's. Godot-free, so the arithmetic that reads it is testable.
  /// </remarks>
  public sealed class GroundRules
  {
    private readonly HashSet<int> _printBlocks;

    public GroundRules(
      long stampLifetimeMillis, int stampRingCapacity, IEnumerable<int> stampBlocks, WearRules wear, BloodRules blood)
    {
      if (stampLifetimeMillis <= 0 || stampRingCapacity <= 0)
      {
        throw new InvalidDataException(
          $"ground rules need a positive print lifetime and ring, got {stampLifetimeMillis} ms and {stampRingCapacity}");
      }

      StampLifetimeMillis = stampLifetimeMillis;
      StampRingCapacity = stampRingCapacity;
      _printBlocks = new HashSet<int>(stampBlocks);
      Wear = wear;
      Blood = blood;
    }

    /// <summary>
    /// Reads the server's rules message.
    /// </summary>
    /// <exception cref="InvalidDataException">on a message missing its wear or blood section, or with numbers no rule can use.</exception>
    public static GroundRules FromProto(global::Bnet.GroundMarkRulesSMSG proto)
    {
      if (proto.Wear == null || proto.Blood == null)
      {
        throw new InvalidDataException("ground mark rules arrived without their wear or blood section");
      }

      var weights = new Dictionary<int, double>();
      foreach (var entry in proto.Wear.BlockWeights)
      {
        weights[(int)entry.BlockId] = entry.Weight;
      }

      var stampBlocks = new List<int>();
      foreach (var block in proto.StampBlocks)
      {
        stampBlocks.Add((int)block);
      }

      var wear = new WearRules(
        (int)proto.Wear.FadeSeconds, (int)proto.Wear.VisibleThreshold,
        (int)proto.Wear.StepWeight, (int)proto.Wear.NeighbourWeight, weights);

      var blood = new BloodRules(
        (int)proto.Blood.FadeSeconds, (int)proto.Blood.VisibleThreshold,
        (int)proto.Blood.SpillWeight, (int)proto.Blood.SpillRadiusTiles);

      return new GroundRules(proto.StampLifetimeMillis, (int)proto.StampRingCapacity, stampBlocks, wear, blood);
    }

    /// <summary>Real milliseconds a fresh footprint lasts.</summary>
    public long StampLifetimeMillis { get; }

    /// <summary>Footprints held per column, the oldest dropped first.</summary>
    public int StampRingCapacity { get; }

    public WearRules Wear { get; }

    public BloodRules Blood { get; }

    /// <summary>Whether a footfall on this block leaves a print. False for a block id nobody listed.</summary>
    public bool TakesPrint(int blockId) => _printBlocks.Contains(blockId);
  }

  /// <summary>What a graded layer has to say about its own fade.</summary>
  public abstract class LevelRules
  {
    protected LevelRules(int fadeSeconds, int visibleThreshold)
    {
      if (fadeSeconds <= 0)
      {
        throw new InvalidDataException($"a layer's fade must be positive, got {fadeSeconds} s");
      }

      FadeSeconds = fadeSeconds;
      VisibleThreshold = visibleThreshold;
    }

    /// <summary>Bestia seconds for a full cell to fade to nothing, untouched.</summary>
    public int FadeSeconds { get; }

    /// <summary>The level below which a cell is not drawn.</summary>
    public int VisibleThreshold { get; }
  }

  /// <summary>What a footfall does to the worn layer.</summary>
  public sealed class WearRules : LevelRules
  {
    private readonly Dictionary<int, double> _weights;

    public WearRules(
      int fadeSeconds, int visibleThreshold, int stepWeight, int neighbourWeight,
      IReadOnlyDictionary<int, double> blockWeights)
      : base(fadeSeconds, visibleThreshold)
    {
      StepWeight = stepWeight;
      NeighbourWeight = neighbourWeight;
      _weights = new Dictionary<int, double>();

      foreach (var (block, weight) in blockWeights)
      {
        _weights[block] = weight;
      }
    }

    /// <summary>Added to the cell stepped onto, before the block's weight.</summary>
    public int StepWeight { get; }

    /// <summary>Added the same way to each of the four cells beside it.</summary>
    public int NeighbourWeight { get; }

    /// <summary>How readily this block wears. Zero for a block that is absent.</summary>
    public double WeightOf(int blockId) => _weights.TryGetValue(blockId, out var weight) ? weight : 0.0;
  }

  /// <summary>What a death does to the bloodied layer.</summary>
  public sealed class BloodRules : LevelRules
  {
    public BloodRules(int fadeSeconds, int visibleThreshold, int spillWeight, int spillRadiusTiles)
      : base(fadeSeconds, visibleThreshold)
    {
      SpillWeight = spillWeight;
      SpillRadiusTiles = spillRadiusTiles;
    }

    /// <summary>Level added at the tile something died on.</summary>
    public int SpillWeight { get; }

    /// <summary>Reach of the pool, falling to nothing just past this many tiles.</summary>
    public int SpillRadiusTiles { get; }
  }
}
