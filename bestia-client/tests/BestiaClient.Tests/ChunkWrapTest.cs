using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client's half of the wrap agreement, checked against the rules the server's <c>WorldWrap</c> states.
  /// </summary>
  /// <remarks>
  /// Every assertion here is really the same one: a chunk address the client worked out for itself has to end up
  /// spelled the way the server would have spelled it. The consequence of getting it wrong is not an exception -
  /// it is a lookup that misses, which the mesher reads as "neighbour not held" and papers over by clamping its
  /// own edge column. That failure is silent and looks like a terrain bug, so it wants pinning here.
  /// </remarks>
  public class ChunkWrapTest
  {
    private static ChunkWrap Wrapped(int across, int down) => new(across, down, true, true);

    [Fact]
    public void An_address_inside_the_world_is_left_alone()
    {
      var wrap = Wrapped(4000, 4000);

      Assert.Equal(new ChunkKey(2000, 1187, 5), wrap.Normalise(new ChunkKey(2000, 1187, 5)));
    }

    /// <summary>One past the last column is the first column, which is the entire point of a seam.</summary>
    [Fact]
    public void One_past_the_eastern_edge_comes_back_from_the_west()
    {
      var wrap = Wrapped(4000, 4000);

      Assert.Equal(new ChunkKey(0, 0, 0), wrap.Normalise(new ChunkKey(4000, 0, 0)));
      Assert.Equal(new ChunkKey(0, 0, 0), wrap.Normalise(new ChunkKey(0, 4000, 0)));
    }

    /// <summary>
    /// The negative case, which a plain <c>%</c> gets wrong and is the reason this is not written inline.
    /// </summary>
    /// <remarks>
    /// <c>-1 % 4000</c> is <c>-1</c> in C# as it is in Kotlin, so a player stepping west off zero would be sent
    /// outside the world rather than to its far edge - and the lookup would miss for a chunk that is held.
    /// </remarks>
    [Fact]
    public void Stepping_west_off_zero_arrives_at_the_far_edge()
    {
      var wrap = Wrapped(4000, 4000);

      Assert.Equal(new ChunkKey(3999, 3999, 0), wrap.Normalise(new ChunkKey(-1, -1, 0)));
    }

    /// <summary>Up is not a loop - the server's phrase, and the reason z is signed.</summary>
    [Fact]
    public void The_vertical_axis_is_never_folded()
    {
      var wrap = Wrapped(4, 4);

      Assert.Equal(-7, wrap.Normalise(new ChunkKey(0, 0, -7)).Z);
      Assert.Equal(148, wrap.Normalise(new ChunkKey(0, 0, 148)).Z);
    }

    [Fact]
    public void An_unwrapped_axis_keeps_its_edge()
    {
      var wrap = new ChunkWrap(4000, 4000, true, false);

      Assert.Equal(new ChunkKey(0, 4000, 0), wrap.Normalise(new ChunkKey(4000, 4000, 0)));
    }

    /// <summary>
    /// A world of edges folds nothing, and that is what an unconfigured renderer holds.
    /// </summary>
    /// <remarks>
    /// Worth its own case because <c>None</c> is <c>default</c>, so this also pins that a zero-initialised
    /// <see cref="ChunkWrap"/> is inert rather than dividing by an extent of nothing.
    /// </remarks>
    [Fact]
    public void None_folds_nothing()
    {
      Assert.Equal(new ChunkKey(4000, -1, 3), ChunkWrap.None.Normalise(new ChunkKey(4000, -1, 3)));
      Assert.Equal(new ChunkKey(4000, -1, 3), default(ChunkWrap).Normalise(new ChunkKey(4000, -1, 3)));
    }

    /// <summary>A wrap flag with no extent behind it cannot fold, so it must not claim to.</summary>
    [Fact]
    public void A_wrap_without_an_extent_is_inert()
    {
      var wrap = new ChunkWrap(0, 0, true, true);

      Assert.False(wrap.WrapX);
      Assert.False(wrap.WrapY);
      Assert.Equal(new ChunkKey(17, 17, 0), wrap.Normalise(new ChunkKey(17, 17, 0)));
    }

    /// <summary>
    /// The distance half of the agreement: across the seam the short way round is the real one.
    /// </summary>
    /// <remarks>
    /// This is what decides whether a chunk gets a collider. Measured the long way, the column immediately east
    /// of a player standing in the last one reads as the full width of the world away and is never given one -
    /// so the player walks onto ground that draws but cannot be stood on.
    /// </remarks>
    [Fact]
    public void Distance_across_the_seam_takes_the_short_way()
    {
      var wrap = Wrapped(4000, 4000);

      Assert.Equal(1, wrap.DeltaX(3999, 0));
      Assert.Equal(-1, wrap.DeltaX(0, 3999));
      Assert.Equal(1, wrap.DeltaY(3999, 0));
    }

    [Fact]
    public void Distance_within_the_world_is_the_plain_difference()
    {
      var wrap = Wrapped(4000, 4000);

      Assert.Equal(3, wrap.DeltaX(100, 103));
      Assert.Equal(-3, wrap.DeltaX(103, 100));
    }

    [Fact]
    public void Distance_on_an_unwrapped_axis_is_never_folded()
    {
      var wrap = new ChunkWrap(4000, 4000, false, false);

      Assert.Equal(-3999, wrap.DeltaX(3999, 0));
    }
  }
}
