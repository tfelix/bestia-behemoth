using System;
using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The shipped palette's slot assignment, pinned.
  /// </summary>
  /// <remarks>
  /// The counterpart to the server's <c>ChunkStoreTest."the block palette is pinned"</c>, which fails when a
  /// material's id or name moves. This fails when a material's *appearance* is left undecided, which is the
  /// failure that renders rather than throws: a new block type gets copied into the palette, nobody says which
  /// texture it draws from, and it ships looking like whatever slot zero happens to be.
  /// </remarks>
  public class BlockAppearanceTest
  {
    /// <summary>
    /// Nothing outside the palette gets a texture by accident.
    /// </summary>
    /// <remarks>
    /// The slot table covers all 256 ids because block ids are a byte on the wire, so roughly two hundred entries
    /// are for materials that do not exist. They have to read as grey. This is the assertion that
    /// <see cref="BlockAppearance.SurfaceSlot.Neutral"/> being zero is a decision rather than a coincidence -
    /// renumber the enum and it fails here instead of repainting two hundred ids in the dark.
    /// </remarks>
    [Fact]
    public void UndeclaredIdsAreNeutral()
    {
      var declared = BlockAppearance.Palette.Select(b => (int)b.Id).ToHashSet();

      for (var id = 0; id < BlockAppearance.Ids; id++)
      {
        if (declared.Contains(id))
        {
          continue;
        }

        Assert.Equal(BlockAppearance.SurfaceSlot.Neutral, BlockAppearance.Current.SlotOf((byte)id));
      }
    }

    /// <summary>
    /// How many materials draw from each texture layer, pinned so that adding one is a decision.
    /// </summary>
    /// <remarks>
    /// A census rather than a row-by-row table: sixty assertions that restate the palette would fail for every
    /// edit including the ones that are fine, and would be updated without being read. A count moves only when
    /// the set of materials changes, which is exactly when somebody should be asked which texture the new one
    /// draws from.
    ///
    /// <para>
    /// The shape of the numbers is the interesting part and worth checking by eye when this fails. Rock carries
    /// forty of the fifty-nine because thirty of those are ore - ore is host rock with metal in it, and the grade
    /// is carried by the tint rather than by a texture of its own.
    /// </para>
    /// </remarks>
    [Fact]
    public void EverySlotHasTheMaterialsItIsMeantTo()
    {
      var census = BlockAppearance.Palette
        .GroupBy(b => b.Slot)
        .ToDictionary(g => g.Key, g => g.Count());

      Assert.Equal(59, BlockAppearance.Palette.Count);

      // Water, lava, and the five worked materials with no texture of their own yet.
      Assert.Equal(7, Count(BlockAppearance.SurfaceSlot.Neutral));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Grass));
      Assert.Equal(1, Count(BlockAppearance.SurfaceSlot.DryGrass));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Sand));
      Assert.Equal(5, Count(BlockAppearance.SurfaceSlot.Soil));

      // Ten beds and broken stone, plus thirty grades of ore.
      Assert.Equal(40, Count(BlockAppearance.SurfaceSlot.Rock));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Snow));

      // Held open on purpose. The first worked material to get its own texture goes here.
      Assert.Equal(0, Count(BlockAppearance.SurfaceSlot.Reserved));

      int Count(BlockAppearance.SurfaceSlot slot) => census.GetValueOrDefault(slot, 0);
    }

    /// <summary>
    /// The handful of assignments that are arguments rather than bookkeeping.
    /// </summary>
    /// <remarks>
    /// Each of these was a judgement the palette's comments defend, and each would look plausible if it were
    /// quietly reversed. Named individually so that reversing one produces a failure that says which argument was
    /// lost, rather than a census that is off by one in two places.
    /// </remarks>
    [Theory]
    // Corrupted ground takes its clean twin's slot: the blight is a change of colour, not of material.
    [InlineData(49, BlockAppearance.SurfaceSlot.Grass)]
    [InlineData(51, BlockAppearance.SurfaceSlot.Sand)]
    // Ore is host rock. The grade lives in the tint, which is the only reason it stays legible.
    [InlineData(100, BlockAppearance.SurfaceSlot.Rock)]
    [InlineData(111, BlockAppearance.SurfaceSlot.Rock)]
    [InlineData(129, BlockAppearance.SurfaceSlot.Rock)]
    // Gravel is what scree and river beds are made of; loose earth under it would read as mud.
    [InlineData(30, BlockAppearance.SurfaceSlot.Rock)]
    // Rubble and cobblestone are worked, but they are worked *stone*.
    [InlineData(66, BlockAppearance.SurfaceSlot.Rock)]
    [InlineData(67, BlockAppearance.SurfaceSlot.Rock)]
    // Thatch and plaster wait for textures of their own rather than share one that fits neither.
    [InlineData(62, BlockAppearance.SurfaceSlot.Neutral)]
    [InlineData(63, BlockAppearance.SurfaceSlot.Neutral)]
    // Dry grass is its own slot for the same reason it is its own block: a third of the land depends on it.
    [InlineData(42, BlockAppearance.SurfaceSlot.DryGrass)]
    public void MaterialDrawsFromSlot(int id, BlockAppearance.SurfaceSlot slot) =>
      Assert.Equal(slot, BlockAppearance.Current.SlotOf((byte)id));

    /// <summary>
    /// Every slot ordinal is a channel the mesher can actually write to.
    /// </summary>
    /// <remarks>
    /// The enum and <see cref="BlockAppearance.Slots"/> are two statements of the same fact in two places, and the
    /// mesher indexes an eight-element array by the first while the vertex format is sized by the second. A ninth
    /// slot added without widening the format is an out-of-range write in a worker thread.
    /// </remarks>
    [Fact]
    public void SlotOrdinalsFitTheVertexFormat()
    {
      var slots = Enum.GetValues<BlockAppearance.SurfaceSlot>();

      Assert.Equal(BlockAppearance.Slots, slots.Length);
      Assert.All(slots, slot => Assert.InRange((int)slot, 0, BlockAppearance.Slots - 1));

      // Two RGBA8 vertex attributes, four weights each. Everything above assumes this split.
      Assert.Equal(0, BlockAppearance.Slots % 4);
    }
  }
}
