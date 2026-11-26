class_name AnnouncementText
## The words of an announcement, the same in the chat and on the banner.


static func of(message: AnnouncementSMSG) -> String:
	var template := TranslationServer.translate("ANNOUNCEMENT_%s" % message.AnnouncementName.to_upper())
	# An argument may itself be a text key, such as a direction; anything else comes back as it is.
	var args := Array(message.Args).map(func(arg: String) -> String: return TranslationServer.translate(arg))
	return template % args if not args.is_empty() else template
