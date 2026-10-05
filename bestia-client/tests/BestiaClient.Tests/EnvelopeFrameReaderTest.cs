using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using BestiaBehemothClient.Bnet.Message;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class EnvelopeFrameReaderTest
  {
    private const int MaxFrameLength = 1048576;

    private readonly EnvelopeFrameReader _reader = new(MaxFrameLength);
    private readonly List<byte[]> _bodies = new();

    [Fact]
    public void A_frame_split_across_reads_is_handed_out_once_whole()
    {
      byte[] stream = Frame(1, 2, 3, 4, 5);

      Feed(stream.AsSpan(0, 3));
      Assert.Empty(_bodies);

      Feed(stream.AsSpan(3));
      Assert.Equal(new byte[] { 1, 2, 3, 4, 5 }, Assert.Single(_bodies));
    }

    [Fact]
    public void Several_frames_in_one_read_are_handed_out_in_order()
    {
      Feed(Frame(1).Concat(Frame(2, 2)).Concat(Frame(3, 3, 3)).ToArray());

      Assert.Equal(new[] { 1, 2, 3 }, _bodies.Select(body => body.Length));
    }

    [Fact]
    public void The_largest_frame_survives_arriving_in_small_reads()
    {
      byte[] body = Enumerable.Range(0, MaxFrameLength).Select(i => (byte)i).ToArray();
      byte[] stream = Frame(body);

      for (int offset = 0; offset < stream.Length; offset += 4096)
      {
        Feed(stream.AsSpan(offset, Math.Min(4096, stream.Length - offset)));
      }

      Assert.Equal(body, Assert.Single(_bodies));
    }

    [Fact]
    public void A_frame_split_inside_its_length_prefix_still_arrives()
    {
      byte[] stream = Frame(7, 7).Concat(Frame(9)).ToArray();

      Feed(stream.AsSpan(0, 7));
      Feed(stream.AsSpan(7));

      Assert.Equal(new byte[] { 9 }, _bodies[1]);
    }

    [Fact]
    public void An_empty_frame_is_still_a_frame()
    {
      Feed(Frame());

      Assert.Empty(Assert.Single(_bodies));
    }

    [Fact]
    public void A_frame_over_the_limit_is_refused_rather_than_allocated()
    {
      byte[] prefix = { 0x7F, 0xFF, 0xFF, 0xFF };

      Assert.Throws<InvalidDataException>(() => Feed(prefix));
    }

    private void Feed(ReadOnlySpan<byte> data)
    {
      _reader.Feed(data, body => _bodies.Add(body.ToArray()));
    }

    private static byte[] Frame(params byte[] body)
    {
      byte[] frame = new byte[4 + body.Length];
      frame[0] = (byte)(body.Length >> 24);
      frame[1] = (byte)(body.Length >> 16);
      frame[2] = (byte)(body.Length >> 8);
      frame[3] = (byte)body.Length;
      body.CopyTo(frame, 4);

      return frame;
    }
  }
}
