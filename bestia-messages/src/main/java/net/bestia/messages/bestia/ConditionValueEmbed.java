package net.bestia.messages.bestia;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

public class ConditionValueEmbed implements Serializable {

	private static final long serialVersionUID = 1L;

	private int currentHealth = 0;
	private int maxHealth = 0;
	private int currentMana = 0;
	private int maxMana = 0;
}