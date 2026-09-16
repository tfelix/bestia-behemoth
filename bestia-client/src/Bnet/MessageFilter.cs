using System;
using System.Collections.Generic;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Chooses message types by name, in zone-server's <c>socket.filter-log-messages</c> syntax: a bare
  /// name allows, a <c>!</c> prefix denies, and no entries at all allows everything.
  /// </summary>
  /// <remarks>
  /// Names are matched whole rather than as substrings, which is the one place this departs from the
  /// server: there an entry is tested against the rendered message, so a deny can match a nested value
  /// by accident.
  /// </remarks>
  public readonly struct MessageFilter
  {
    private readonly HashSet<string> _allowed;
    private readonly HashSet<string> _denied;

    private MessageFilter(HashSet<string> allowed, HashSet<string> denied)
    {
      _allowed = allowed;
      _denied = denied;
    }

    public static MessageFilter Parse(IEnumerable<string> entries)
    {
      var allowed = new HashSet<string>();
      var denied = new HashSet<string>();

      foreach (var entry in entries ?? Array.Empty<string>())
      {
        var trimmed = entry?.Trim();
        if (string.IsNullOrEmpty(trimmed))
        {
          continue;
        }

        if (trimmed[0] == '!')
        {
          denied.Add(trimmed.Substring(1));
        }
        else
        {
          allowed.Add(trimmed);
        }
      }

      return new MessageFilter(allowed, denied);
    }

    public bool Allows(string name)
    {
      if (_denied == null)
      {
        return true;
      }

      return (_allowed.Count == 0 || _allowed.Contains(name)) && !_denied.Contains(name);
    }
  }
}
