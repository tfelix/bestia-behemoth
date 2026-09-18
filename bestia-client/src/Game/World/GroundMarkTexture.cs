using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// What has lasted on the ground near the player, as one texture the terrain shader samples by world
  /// position.
  /// </summary>
  /// <remarks>
  /// <b>One texel per square metre, one mark per channel</b>, in the order <see cref="GroundLayers"/>
  /// declares. That matches the server's own lattice exactly, so a column's cells copy in without resampling.
  ///
  /// <para>
  /// <b>Why a texture and not vertex attributes.</b> All four <c>CUSTOM</c> channels are already spent on the
  /// material splat (see <see cref="Mesh.BlockAppearance.Slots"/>), so there was nowhere to put this. Sampling
  /// by world position is better anyway: a mark costs no remesh at all, where a vertex attribute would mean
  /// rebuilding a chunk's geometry every time somebody walked across it.
  /// </para>
  ///
  /// <para>
  /// <b>Addressing is toroidal, which is what keeps it small.</b> The texture covers
  /// <see cref="ExtentMetres"/> of world and repeats; the shader's <c>repeat_enable</c> does the wrapping for
  /// free. Walking scrolls through it rather than reuploading it, and because the span is wider than the view
  /// can ever be, two columns on screen can never collide on a texel. A column that leaves the view is cleared
  /// so its marks cannot reappear half a kilometre away.
  /// </para>
  ///
  /// <para>
  /// <b>One upload per frame at most, and one marshalled call with it.</b> The pixels live in a plain
  /// <c>byte[]</c> and only become an <c>Image</c> on <see cref="Flush"/>, which the renderer calls once a
  /// frame. Writing through <c>Image.SetPixel</c> instead would be a marshalled call per texel - a thousand
  /// of them per column - to build something Godot re-uploads whole anyway.
  /// </para>
  /// </remarks>
  public sealed class GroundMarkTexture
  {
    /// <summary>
    /// Texels across, which at one metre each is also the span in metres before the addressing repeats.
    /// </summary>
    /// <remarks>
    /// 512 m against a view 352 m across at the default <c>view-radius-chunks</c> of 5. The margin is what
    /// guarantees no two visible columns share a texel; raising the view radius past 7 needs this raised too,
    /// and <see cref="Fits"/> is what says so out loud rather than leaving smeared marks to be puzzled over.
    /// </remarks>
    public const int Size = 512;

    /// <summary>Metres per texel. One, so a server cell is a texel and nothing is resampled.</summary>
    public const float MetresPerTexel = 1.0f;

    public const float ExtentMetres = Size * MetresPerTexel;

    /// <summary>One <c>RGBA8</c> texel per square metre, a channel per layer. See the class note.</summary>
    private readonly byte[] _pixels = new byte[Size * Size * 4];

    private readonly ImageTexture _texture;
    private bool _dirty;

    public GroundMarkTexture()
    {
      _texture = ImageTexture.CreateFromImage(
        Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, _pixels));
    }

    public Texture2D Texture => _texture;

    /// <summary>
    /// Whether a view this wide fits inside the span before the addressing repeats.
    /// </summary>
    /// <remarks>
    /// False means two columns on opposite sides of the view land on the same texels and each overwrites the
    /// other's marks - which looks like marks flickering or appearing where nobody walked, and is very hard to
    /// recognise as an addressing problem after the fact.
    /// </remarks>
    public static bool Fits(int viewRadiusChunks, int chunkSize) =>
      (2 * viewRadiusChunks + 1) * chunkSize <= ExtentMetres;

    /// <summary>Writes one column's cells for every channel it has, and clears the channels it does not.</summary>
    /// <param name="cells">Per channel, the column's nibbles, or null for a channel with nothing here.</param>
    public void WriteColumn(int chunkX, int chunkY, int chunkSize, byte[][] cells)
    {
      for (var localY = 0; localY < chunkSize; localY++)
      {
        for (var localX = 0; localX < chunkSize; localX++)
        {
          var at = TexelAt(
            (long)chunkX * chunkSize + localX,
            (long)chunkY * chunkSize + localY);

          for (var channel = 0; channel < GroundLayers.Channels; channel++)
          {
            _pixels[at + channel] = LevelOf(cells, channel, chunkSize, localX, localY);
          }
        }
      }

      _dirty = true;
    }

    /// <summary>
    /// Clears a column, which is what a client stops holding it must do.
    /// </summary>
    /// <remarks>
    /// Not merely tidiness: the addressing wraps, so a column left set would show its marks again on ground
    /// <see cref="ExtentMetres"/> away that nothing has ever happened to.
    /// </remarks>
    public void ClearColumn(int chunkX, int chunkY, int chunkSize)
    {
      for (var localY = 0; localY < chunkSize; localY++)
      {
        for (var localX = 0; localX < chunkSize; localX++)
        {
          var at = TexelAt(
            (long)chunkX * chunkSize + localX,
            (long)chunkY * chunkSize + localY);

          for (var channel = 0; channel < GroundLayers.Channels; channel++)
          {
            _pixels[at + channel] = 0;
          }
        }
      }

      _dirty = true;
    }

    /// <summary>Uploads if anything changed. Cheap and idempotent when nothing did.</summary>
    /// <returns>whether an upload actually happened, which is what a caller logs rather than guesses at</returns>
    public bool Flush()
    {
      if (!_dirty)
      {
        return false;
      }

      _texture.Update(Image.CreateFromData(Size, Size, false, Image.Format.Rgba8, _pixels));
      _dirty = false;

      return true;
    }

    public void Clear()
    {
      System.Array.Clear(_pixels);
      _dirty = true;
    }

    /// <summary>Where one square metre of the world lives in <see cref="_pixels"/>.</summary>
    /// <remarks>
    /// Modulo rather than an offset, because the addressing is toroidal - see the class note. Kept positive by
    /// hand: C# <c>%</c> keeps the sign of the dividend, and half this world has negative coordinates.
    /// </remarks>
    private static int TexelAt(long worldX, long worldY)
    {
      var x = (int)(((worldX % Size) + Size) % Size);
      var y = (int)(((worldY % Size) + Size) % Size);

      return (y * Size + x) * 4;
    }

    /// <summary>One channel's level at a cell, as the 0..255 a texel holds.</summary>
    private static byte LevelOf(byte[][] cells, int channel, int chunkSize, int localX, int localY)
    {
      if (channel >= cells.Length)
      {
        return 0;
      }

      // Scaled so the strongest level is a full byte: 15 becomes 255, not 15/256th of the way up. The shader
      // reads this as 0..1 and a mark that never exceeded 6% would be invisible.
      return (byte)(GroundLayerCells.LevelAt(cells[channel], chunkSize, localX, localY) * 255
        / GroundLayerCells.MaxLevel);
    }
  }
}
