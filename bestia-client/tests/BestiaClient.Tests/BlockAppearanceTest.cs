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
    /// forty-nine of the sixty-two because forty-two of those are ore and gem - both are host rock with
    /// something in it, and the grade is carried by the tint rather than by a texture of its own.
    /// </para>
    /// </remarks>
    [Fact]
    public void EverySlotHasTheMaterialsItIsMeantTo()
    {
      var census = BlockAppearance.Palette
        .GroupBy(b => b.Slot)
        .ToDictionary(g => g.Key, g => g.Count());

      Assert.Equal(62, BlockAppearance.Palette.Count);

      // Water, lava, and masonry - the last worked material with no texture of its own yet. It was five
      // worked materials until the building ones left the palette for props.
      Assert.Equal(3, Count(BlockAppearance.SurfaceSlot.Neutral));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Grass));
      Assert.Equal(1, Count(BlockAppearance.SurfaceSlot.DryGrass));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Sand));

      // Dirt and its blighted twin. Peat and clay used to be here and are MUD on Wetland now; a corrupted bog
      // still lands here, because MUD's blighted twin is BLIGHTED_DIRT.
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Soil));

      // Five beds, gravel and cobblestone, plus forty-two grades of ore and gem.
      Assert.Equal(49, Count(BlockAppearance.SurfaceSlot.Rock));
      Assert.Equal(2, Count(BlockAppearance.SurfaceSlot.Snow));

      // MUD, and it is the whole reason the slot stopped being Reserved: wet ground is not dry ground in a
      // darker tint. See SurfaceSlot.Wetland.
      Assert.Equal(1, Count(BlockAppearance.SurfaceSlot.Wetland));

      // None. Scorched ground is not a block and cannot be one - the chunk wire format can only remove a voxel
      // - so it reaches the mesher as a per-chunk mask and no palette row maps to it. A row appearing here
      // means somebody tried to make burnt ground a BlockType, which cannot be delivered.
      Assert.Equal(0, Count(BlockAppearance.SurfaceSlot.Scorched));

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
    [InlineData(16, BlockAppearance.SurfaceSlot.Grass)]     // BLIGHTED_GRASS
    [InlineData(18, BlockAppearance.SurfaceSlot.Sand)]      // BLIGHTED_SAND
    // And a corrupted bog lands on Soil rather than Wetland, because MUD's twin is BLIGHTED_DIRT. A real loss
    // of the wet grain and a deliberate one - the server's blight table is not a bijection.
    [InlineData(17, BlockAppearance.SurfaceSlot.Soil)]      // BLIGHTED_DIRT
    // Ore and gem are both host rock. The grade lives in the tint, which is the only reason it stays legible.
    [InlineData(21, BlockAppearance.SurfaceSlot.Rock)]      // ORE_COPPER_SMALL, first of the band
    [InlineData(32, BlockAppearance.SurfaceSlot.Rock)]      // ORE_GOLD_RICH
    [InlineData(62, BlockAppearance.SurfaceSlot.Rock)]      // GEM_DIAMOND_RICH, last of the palette
    // Gravel is what scree and river beds are made of; loose earth under it would read as mud.
    [InlineData(9, BlockAppearance.SurfaceSlot.Rock)]       // GRAVEL
    // Cobblestone is worked, but it is worked *stone*.
    [InlineData(20, BlockAppearance.SurfaceSlot.Rock)]      // COBBLESTONE
    // Masonry waits for a texture of its own rather than share one that fits it badly. The last of the worked
    // materials to be in this position - the other five left the palette when buildings became props.
    [InlineData(19, BlockAppearance.SurfaceSlot.Neutral)]   // MASONRY
    // Dry grass is its own slot for the same reason it is its own block: a third of the land depends on it.
    [InlineData(14, BlockAppearance.SurfaceSlot.DryGrass)]  // DRY_GRASS
    // And the slot that was held in reserve, spent on the one material a tint could not express.
    [InlineData(12, BlockAppearance.SurfaceSlot.Wetland)]   // MUD
    public void MaterialDrawsFromSlot(int id, BlockAppearance.SurfaceSlot slot) =>
      Assert.Equal(slot, BlockAppearance.Current.SlotOf((byte)id));

    /// <summary>
    /// Every slot ordinal is a channel the mesher can actually write to.
    /// </summary>
    /// <remarks>
    /// The mesher indexes a <see cref="BlockAppearance.Slots"/>-element array by the ordinal and the vertex
    /// format is sized by the same constant, so a slot past the end is an out-of-range write in a worker thread.
    ///
    /// <para>
    /// <b>Fits, not fills.</b> This asserted equality while the enum happened to use all eight channels, which
    /// conflated two different facts: that no ordinal escapes the format, and that no channel goes spare. The
    /// second was never a requirement, and stopped being true the moment the format went to sixteen for
    /// <c>Scorched</c> - the spare channels are deliberate headroom, and a test demanding they be filled would
    /// have to be edited by whoever fills one, which is the opposite of a guard.
    /// </para>
    /// </remarks>
    [Fact]
    public void SlotOrdinalsFitTheVertexFormat()
    {
      var slots = Enum.GetValues<BlockAppearance.SurfaceSlot>();

      Assert.True(
        slots.Length <= BlockAppearance.Slots,
        $"{slots.Length} slots declared against a format sized for {BlockAppearance.Slots}");
      Assert.All(slots, slot => Assert.InRange((int)slot, 0, BlockAppearance.Slots - 1));

      // Ordinals are dense from zero, so the texture array layer count and the enum agree.
      Assert.Equal(
        Enumerable.Range(0, slots.Length),
        slots.Select(slot => (int)slot).OrderBy(ordinal => ordinal));

      // Four RGBA8 vertex attributes, four weights each. Everything above assumes this split.
      Assert.Equal(0, BlockAppearance.Slots % 4);
    }
  }
}
