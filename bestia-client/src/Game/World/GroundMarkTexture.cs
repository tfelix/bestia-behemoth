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
  /// <b>One upload per frame at most.</b> Godot re-uploads the whole image on <c>Update</c>, so writes are
  /// batched and <see cref="Flush"/> is called once per frame by the renderer. At this size that is a
  /// megabyte, and only on a frame where something actually changed.
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

    private readonly Image _image;
    private readonly ImageTexture _texture;
    private bool _dirty;

    public GroundMarkTexture()
    {
      _image = Image.CreateEmpty(Size, Size, false, Image.Format.Rgba8);
      _image.Fill(new Color(0, 0, 0, 0));
      _texture = ImageTexture.CreateFromImage(_image);
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
          var colour = new Color(
            LevelOf(cells, 0, chunkSize, localX, localY),
            LevelOf(cells, 1, chunkSize, localX, localY),
            LevelOf(cells, 2, chunkSize, localX, localY),
            LevelOf(cells, 3, chunkSize, localX, localY));

          SetTexel(chunkX * chunkSize + localX, chunkY * chunkSize + localY, colour);
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
          SetTexel(chunkX * chunkSize + localX, chunkY * chunkSize + localY, new Color(0, 0, 0, 0));
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

      _texture.Update(_image);
      _dirty = false;

      return true;
    }

    public void Clear()
    {
      _image.Fill(new Color(0, 0, 0, 0));
      _dirty = true;
    }

    private void SetTexel(long worldX, long worldY, Color colour)
    {
      // Modulo rather than an offset, because the addressing is toroidal - see the class note. Kept positive
      // by hand: C# `%` keeps the sign of the dividend, and half this world has negative coordinates.
      var x = (int)(((worldX % Size) + Size) % Size);
      var y = (int)(((worldY % Size) + Size) % Size);

      _image.SetPixel(x, y, colour);
    }

    /// <summary>One channel's level at a cell, as the 0..1 the shader wants.</summary>
    private static float LevelOf(byte[][] cells, int channel, int chunkSize, int localX, int localY)
    {
      if (channel >= cells.Length)
      {
        return 0f;
      }

      return GroundLayerCells.UnitAt(cells[channel], chunkSize, localX, localY);
    }
  }
}
