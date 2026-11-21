namespace BestiaBehemothClient.Game.UI.Chat
{
  /// <summary>Who a <c>/w</c> line names, and what it says to them.</summary>
  public static class WhisperAddress
  {
    private const char Quote = '"';

    /// <summary>
    /// Splits what follows <c>/w</c> into the master's name and the text. A name of two words has to be quoted,
    /// since nothing else says where it ends. The name is empty when the line names nobody.
    /// </summary>
    public static (string Target, string Text) Split(string afterPrefix)
    {
      var rest = afterPrefix.Trim();

      return rest.StartsWith(Quote) ? SplitQuoted(rest) : SplitAtFirstSpace(rest);
    }

    private static (string Target, string Text) SplitQuoted(string rest)
    {
      var closing = rest.IndexOf(Quote, 1);
      if (closing < 0)
      {
        return (string.Empty, string.Empty);
      }

      return (rest[1..closing].Trim(), rest[(closing + 1)..].Trim());
    }

    private static (string Target, string Text) SplitAtFirstSpace(string rest)
    {
      var space = rest.IndexOf(' ');
      if (space < 0)
      {
        return (rest, string.Empty);
      }

      return (rest[..space], rest[(space + 1)..].Trim());
    }
  }
}
