package net.bestia.zoneserver.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.ShortcutDAO;

/**
 * Service for managing and saving shortcuts coming from the clients to the
 * server.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ShortcutService {
	
	private static final int SHORTCUTS_PER_ROW = 5;
	private static final int MAX_ACCOUNT_SHORTCUTS = 1 * SHORTCUTS_PER_ROW;
	private static final int MAX_BESTIA_SHORTCUTS = 3 * SHORTCUTS_PER_ROW;
	
	private final ShortcutDAO shortcutDao;

	@Autowired
	public ShortcutService(ShortcutDAO shortcutDao) {
		
		this.shortcutDao = Objects.requireNonNull(shortcutDao);
		
	}
	
	// TODO Handle the saveing.
}
