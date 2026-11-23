class_name AnnouncementText
## The words of an announcement, the same in the chat and on the banner.


static func of(message: AnnouncementSMSG) -> String:
	var template := TranslationServer.translate("ANNOUNCEMENT_%s" % message.AnnouncementName.to_upper())
	return template % Array(message.Args) if not message.Args.is_empty() else template
