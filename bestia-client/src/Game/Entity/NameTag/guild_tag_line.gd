class_name GuildTagLine

## The name tag's second line for a member of a guild, as Ragnarok Online writes it: the guild, then the rank.


static func of(tag: GuildTagComponentSMSG) -> String:
	if tag == null:
		return ""
	return "%s [%s]" % [tag.GuildName, tag.RankTitle]


static func under(entity_name: String, guild_line: String) -> String:
	if guild_line.is_empty():
		return entity_name
	return "%s\n%s" % [entity_name, guild_line]
