using System;
using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>One tile of the ground and how much to add to it.</summary>
  public readonly struct CellAmount
  {
    public CellAmount(long voxelX, long voxelY, int amount)
    {
      VoxelX = voxelX;
      VoxelY = voxelY;
      Amount = amount;
    }

    public long VoxelX { get; }

    public long VoxelY { get; }

    public int Amount { get; }
  }

  /// <summary>
  /// The raw block id at the top of a tile, searched near <paramref name="nearZ"/>, or -1 when it cannot be known
  /// - the chunk is not held, or nothing is there.
  /// </summary>
  public delegate int SurfaceBlockAt(long voxelX, long voxelY, int nearZ);

  /// <summary>
  /// How much a footfall wears the ground and how much a death bleeds into it, as the server works them out.
  /// </summary>
  /// <remarks>
  /// Ports of <c>MarkingGroundTrample.wearUnder</c> and <c>GroundBloodRegistry.spill</c>, both taking their
  /// numbers from <see cref="GroundRules"/>. Pure, so each amount is pinned by a test without an engine.
  /// </remarks>
  public static class GroundSpread
  {
    private static readonly (int Dx, int Dy)[] Neighbours = { (1, 0), (-1, 0), (0, 1), (0, -1) };

    /// <summary>
    /// Fills <paramref name="into"/> with what a step onto (<paramref name="voxelX"/>, <paramref name="voxelY"/>)
    /// wears: the tile itself, then the four beside it.
    /// </summary>
    /// <remarks>
    /// Ground that does not wear costs one lookup and spreads nothing, so a trail never smears onto the paving
    /// beside it. Each neighbour is judged on its own block rather than assumed to match the centre.
    /// </remarks>
    public static void Wear(
      WearRules rules, long voxelX, long voxelY, int nearZ, SurfaceBlockAt blockAt, List<CellAmount> into)
    {
      into.Clear();

      var underfoot = rules.WeightOf(blockAt(voxelX, voxelY, nearZ));
      if (underfoot <= 0.0)
      {
        return;
      }

      Add(into, voxelX, voxelY, (int)(rules.StepWeight * underfoot));

      if (rules.NeighbourWeight <= 0)
      {
        return;
      }

      foreach (var (dx, dy) in Neighbours)
      {
        var beside = rules.WeightOf(blockAt(voxelX + dx, voxelY + dy, nearZ));
        if (beside <= 0.0)
        {
          continue;
        }

        Add(into, voxelX + dx, voxelY + dy, (int)(rules.NeighbourWeight * beside));
      }
    }

    /// <summary>
    /// Fills <paramref name="into"/> with the pool a death at (<paramref name="voxelX"/>, <paramref name="voxelY"/>)
    /// soaks in, strongest at the middle and falling to nothing just past the radius.
    /// </summary>
    public static void Blood(BloodRules rules, long voxelX, long voxelY, List<CellAmount> into)
    {
      into.Clear();

      var radius = rules.SpillRadiusTiles;

      for (var dy = -radius; dy <= radius; dy++)
      {
        for (var dx = -radius; dx <= radius; dx++)
        {
          var distance = Math.Sqrt(dx * dx + dy * dy);
          if (distance > radius)
          {
            continue;
          }

          Add(into, voxelX + dx, voxelY + dy, (int)(rules.SpillWeight * (1.0 - distance / (radius + 1.0))));
        }
      }
    }

    private static void Add(List<CellAmount> into, long voxelX, long voxelY, int amount)
    {
      if (amount > 0)
      {
        into.Add(new CellAmount(voxelX, voxelY, amount));
      }
    }
  }
}
