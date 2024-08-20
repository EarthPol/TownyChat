package com.palmergames.bukkit.TownyChat.channels;

import com.palmergames.bukkit.TownyChat.Chat;
import com.palmergames.bukkit.TownyChat.TownyChatFormatter;
import com.palmergames.bukkit.TownyChat.config.ChatSettings;
import com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent;
import com.palmergames.bukkit.TownyChat.events.PlayerJoinChatChannelEvent;
import com.palmergames.bukkit.TownyChat.listener.LocalTownyChatEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.util.Colors;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.dynmap.DynmapAPI;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UnknownFormatConversionException;
import java.util.stream.Collectors;

public class StandardChannel extends Channel {

	private Chat plugin;
	
	public StandardChannel(Chat instance, String name) {
		super(name);
		this.plugin = instance;
	}

	@Override
	public void chatProcess(AsyncPlayerChatEvent event) {
		channelTypes channelType = this.getType();
		Player player = event.getPlayer();
		boolean notifyjoin = false;

		Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
		if (resident == null) {
			return;
		}

		Town town = TownyAPI.getInstance().getResidentTownOrNull(resident);
		Nation nation = TownyAPI.getInstance().getResidentNationOrNull(resident);

		// If the channel would require a town/nation which is null, cancel and fail early.
		if ((town == null && channelType.equals(channelTypes.TOWN)) ||
				(nation == null && (channelType.equals(channelTypes.NATION) || channelType.equals(channelTypes.ALLIANCE)))) {
			event.setCancelled(true);
			return;
		}

		// If player sends a message to a channel they have left, rejoin them to the channel.
		if (isAbsent(player.getName())) {
			join(player);
			notifyjoin = true;
			Bukkit.getPluginManager().callEvent(new PlayerJoinChatChannelEvent(player, this));
		}

		// Check if the channel name is either "GLOBAL" or "GENERAL".
		if (this.getName().equalsIgnoreCase("GLOBAL") || this.getName().equalsIgnoreCase("GENERAL") || this.getName().equalsIgnoreCase("TRADE")) {
			// Cancel the event to prevent the default chat handling.
			event.setCancelled(true);

			// Gather town and nation information
			String townName = town != null ? town.getName() : "No Town";
			String townMayor = town != null ? town.getMayor().getName() : "No Mayor";
			int townBlocks = town != null ? town.getNumTownBlocks() : 0;
			int townMaxBlocks = town != null ? town.getMaxTownBlocks() : 0;
			int numOfResidents = town != null ? town.getNumResidents() : 0;

			String nationName = nation != null ? nation.getName() : "No Nation";
			String nationKing = nation != null ? nation.getKing().getName() : "No King";
			int numOfTowns = nation != null ? nation.getNumTowns() : 0;
			int numOfNationResidents = nation != null ? nation.getNumResidents() : 0;

			// Create hover text for the nation component
			Component nationHoverText = Component.text()
					.append(Component.text(".oOo.______[ " + nationName + " ].______.oOo", NamedTextColor.DARK_GREEN))
					.append(Component.newline())
					.append(Component.text("King: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(nationKing, NamedTextColor.GREEN))
					.append(Component.newline())
					.append(Component.text("Number of Towns: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(numOfTowns, NamedTextColor.GREEN))
					.append(Component.newline())
					.append(Component.text("Number of Residents: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(numOfNationResidents, NamedTextColor.GREEN))
					.build();

			// Create hover text for the town component
			Component townHoverText = Component.text()
					.append(Component.text(".oOo.______[ " + townName + " ].______.oOo", NamedTextColor.DARK_GREEN))
					.append(Component.newline())
					.append(Component.text("Mayor: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(townMayor, NamedTextColor.GREEN))
					.append(Component.newline())
					.append(Component.text("Town Size: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(townBlocks + " / " + townMaxBlocks, NamedTextColor.GREEN))
					.append(Component.newline())
					.append(Component.text("Number of Residents: ", NamedTextColor.DARK_GREEN))
					.append(Component.text(numOfResidents, NamedTextColor.GREEN))
					.build();

			// Create clickable nation component if in a nation
			Component nationComponent = null;
			if (nation != null) {
				nationComponent = Component.text()
						.content(nationName)
						.color(NamedTextColor.GOLD)
						.hoverEvent(HoverEvent.showText(nationHoverText))
						.clickEvent(ClickEvent.runCommand("/n " + nation.getName()))
						.build();
			}

			// Create clickable town component if in a town
			Component townComponent = null;
			if (town != null) {
				townComponent = Component.text()
						.content(townName)
						.color(NamedTextColor.DARK_AQUA)
						.hoverEvent(HoverEvent.showText(townHoverText))
						.clickEvent(ClickEvent.runCommand("/t " + town.getName()))
						.build();
			}

			// Determine player name color based on permissions
			NamedTextColor playerColor = NamedTextColor.GRAY;  // Default color
			if (player.hasPermission("group.premium")) {
				playerColor = NamedTextColor.LIGHT_PURPLE;
			}
			if (player.hasPermission("group.helper")) {
				playerColor = NamedTextColor.YELLOW;
			}
			if (player.hasPermission("group.mod")) {
				playerColor = NamedTextColor.GREEN;  // Light green
			}
			if (player.hasPermission("group.admin")) {
				playerColor = NamedTextColor.RED;
			}
			if (player.hasPermission("group.developer")) {
				playerColor = NamedTextColor.DARK_BLUE;
			}
			if (player.hasPermission("group.owner")) {
				playerColor = NamedTextColor.DARK_AQUA;
			}

			Component playerComponent;

			// Check if the player is the mayor of their town
			if (town != null && town.getMayor().getName().equals(player.getName())) {
				// Mayor, prepend the ♔ icon
				playerComponent = Component.text()
						.content("♔ " + player.getName())
						.color(playerColor)
						.hoverEvent(HoverEvent.showText(Component.text("Click to send a message.")))
						.clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
						.build();
			} else {
				// Not a mayor, display the name without the icon
				playerComponent = Component.text()
						.content(player.getName())
						.color(playerColor)
						.hoverEvent(HoverEvent.showText(Component.text("Click to send a message.")))
						.clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "))
						.build();
			}

			// Build the final message component dynamically based on presence of nation and town
			TextComponent.Builder messageComponentBuilder = Component.text().color(NamedTextColor.GRAY);

			// Add [Trade] at the front if the channel is "Trade"
			if (this.getName().equalsIgnoreCase("TRADE")) {
				messageComponentBuilder = messageComponentBuilder
						.append(Component.text("[", NamedTextColor.DARK_AQUA))  // Opening bracket in dark aqua
						.append(Component.text("Trade", NamedTextColor.GREEN))  // Trade in green
						.append(Component.text("] ", NamedTextColor.DARK_AQUA));  // Closing bracket in dark aqua
			}

			if (nationComponent != null && townComponent != null) {
				messageComponentBuilder = messageComponentBuilder
						.append(Component.text("[")) // Opening bracket
						.append(nationComponent) // Nation component
						.append(Component.text("|", NamedTextColor.GRAY)) // Separator
						.append(townComponent) // Town component
						.append(Component.text("] "));
			} else if (townComponent != null) {
				messageComponentBuilder = messageComponentBuilder
						.append(Component.text("[")) // Opening bracket
						.append(townComponent) // Town component
						.append(Component.text("] "));
			}

			if (this.getName().equalsIgnoreCase("TRADE")) {
				messageComponentBuilder = messageComponentBuilder
						.append(playerComponent) // Player component
						.append(Component.text(" • ", NamedTextColor.GRAY)) // Separator
						.append(Component.text(event.getMessage()).color(NamedTextColor.DARK_AQUA)); // Message content
			} else {
				messageComponentBuilder = messageComponentBuilder
						.append(playerComponent) // Player component
						.append(Component.text(" • ", NamedTextColor.GRAY)) // Separator
						.append(Component.text(event.getMessage()).color(NamedTextColor.WHITE)); // Message content
			}

			// Build the final component from the builder
			Component messageComponent = messageComponentBuilder.build();

			String logMessage = String.format("[%s|%s] %s • %s",
					nation != null ? nation.getName() : "No Nation",
					town != null ? town.getName() : "No Town",
					player.getName(),
					event.getMessage());

			Bukkit.getLogger().info(logMessage);

			// Send the message to the player and all recipients
			for (Player recipient : event.getRecipients()) {
				recipient.sendMessage(messageComponent);
			}

		} else {
			// If the channel is not "GLOBAL" or "GENERAL", proceed with the default chat processing.
			String format = ChatSettings.getChannelFormat(player, channelType);

			// Get the list of message recipients.
			Set<Player> recipients = getRecipients(player, town, nation, channelType, event.getRecipients());

			// Parse any PAPI placeholders.
			String newFormat = format;
			if (Chat.usingPlaceholderAPI) {
				newFormat = PlaceholderAPI.setPlaceholders(player, format);
			}

			// Only modify GLOBAL channelType chat (general and local chat channels) if isModifyChat() is true.
			if (!(channelType.equals(channelTypes.GLOBAL) && !ChatSettings.isModify_chat())) {
				applyFormats(event, format, newFormat, resident);
			}

			// Set recipients for Bukkit to send this message to.
			try {
				event.getRecipients().clear();
				event.getRecipients().addAll(recipients);
			} catch (UnsupportedOperationException ignored) {}

			// If the server has marked this Channel as hooked, fire the AsyncChatHookEvent.
			if (isHooked()) {
				if (!sendOffHookedMessage(event, channelType)) {
					event.setCancelled(true);
					return;
				}
			}

			// Send spy message if this was never hooked.
			if (!isHooked()) {
				sendSpyMessage(event, channelType);
			}

			// Play the channel sound, if used.
			tryPlayChannelSound(event.getRecipients());
		}

		if (notifyjoin) {
			TownyMessaging.sendMessage(player, "You join " + Colors.translateColorCodes(getMessageColour()) + getName());
		}

		// Perform any last channel-specific functions like logging this chat and relaying to Dynmap.
		switch (channelType) {
			case TOWN:
			case NATION:
			case ALLIANCE:
			case DEFAULT:
				break;
			case PRIVATE:
			case GLOBAL:
				tryPostToDynmap(player, event.getMessage());
				break;
		}
	}


	private Set<Player> getRecipients(Player player, Town town, Nation nation, channelTypes channelType, Set<Player> recipients) {
		return switch (channelType) {
		case TOWN -> new HashSet<>(findRecipients(player, TownyAPI.getInstance().getOnlinePlayers(town)));
		case NATION -> new HashSet<>(findRecipients(player, TownyAPI.getInstance().getOnlinePlayers(nation)));
		case ALLIANCE -> new HashSet<>(findRecipients(player, TownyAPI.getInstance().getOnlinePlayersAlliance(nation)));
		case DEFAULT -> new HashSet<>(findRecipients(player, new ArrayList<>(recipients)));
		case GLOBAL, PRIVATE -> new HashSet<>(findRecipients(player, new ArrayList<>(recipients)));
		};
	}

	/**
	 * Compile a list of valid recipients for this message.
	 *
	 * @param sender
	 * @param playerList
	 * @return Set containing a list of players for this message.
	 */
	private Set<Player> findRecipients(Player sender, List<Player> playerList) {
		// Refresh the potential channels a player can see, if they are not currently in the channel.
		playerList.stream().forEach(p -> refreshPlayer(this, p));
		return playerList.stream()
				.filter(p -> hasListenPermission(p)) // Check permission.
				.filter(p -> testDistance(sender, p, getRange())) // Within range.
				.filter(p -> !plugin.isIgnoredByEssentials(sender, p)) // Check essentials ignore.
				.filter(p -> !isAbsent(p.getName())) // Check if player is purposefully absent.
				.filter(p -> !isFocusedToAnotherChannel(p)) // Check if the player is listening to a single channel.
				.collect(Collectors.toSet());
	}

	private boolean isFocusedToAnotherChannel(Player p) {
		Resident resident = TownyAPI.getInstance().getResident(p);
		return this.isIgnoreable() && resident != null && resident.hasMode("ignoreotherchannels") && !plugin.getPlayerChannel(p).equals(this);
	}

	private void refreshPlayer(Channel channel, Player player) {
		if (!channel.isPresent(player.getName()))
			channel.forgetPlayer(player);
	}

	/**
	 * Check the distance between players and return a result based upon the range setting
	 * -1 = no limit
	 * 0 = same world
	 * any positive value = distance in blocks
	 *
	 * @param player1
	 * @param player2
	 * @param range
	 * @return true if in range
	 */
	private boolean testDistance(Player player1, Player player2, double range) {
		
		// unlimited range (all worlds)
		if (range == -1)
			return true;
		
		// Same world only
		if (range == 0)
			return player1.getWorld().equals(player2.getWorld());
		
		// Range check (same world)
		return player1.getWorld().equals(player2.getWorld()) && 
				player1.getLocation().distance(player2.getLocation()) < range;
	}

	private void trySendingAloneMessage(Player sender, Set<Player> recipients) {
		if (ChatSettings.isUsingAloneMessage() &&
				recipients.stream().filter(p -> sender.canSee(p)).count() < 2) // sender will usually be a recipient of their own message.
			TownyMessaging.sendMessage(sender, ChatSettings.getUsingAloneMessageString());
	}

	private void applyFormats(AsyncPlayerChatEvent event, String originalFormat, String workingFormat, Resident resident) {
		// Parse out our own channelTag and msgcolour tags.
		String newFormat = parseTagAndMsgColour(workingFormat);
		// Attempt to apply the new format.
		catchFormatConversionException(event, originalFormat, newFormat);
		
		// Fire the LocalTownyChatEvent.
		LocalTownyChatEvent chatEvent = new LocalTownyChatEvent(event, resident);
		// Format the chat line, replacing the TownyChat chat tags.
		newFormat = TownyChatFormatter.getChatFormat(chatEvent);
		// Attempt to apply the new format.
		catchFormatConversionException(event, originalFormat, newFormat);
	}

	private String parseTagAndMsgColour(String format) {
		return format
			.replace("{channelTag}", Colors.translateColorCodes(getChannelTag() != null ? getChannelTag() : ""))
			.replace("{msgcolour}", Colors.translateColorCodes(getMessageColour() != null ? getMessageColour() : ""));
	}

	private void catchFormatConversionException(AsyncPlayerChatEvent event, String format, String newFormat) {
		try {
			event.setFormat(newFormat);
		} catch (UnknownFormatConversionException e) {
			// This exception is usually thrown when a PAPI placeholder did not get parsed
			// and has left behind a % symbol followed by something that String#format
			// cannot handle.
			boolean percentSymbol = format.contains("%" + e.getConversion());
			String errmsg = "TownyChat tried to apply a chat format that is not allowed: '" +
					newFormat + "', because of the " + e.getConversion() + " symbol" +
					(percentSymbol ? ", found after a %. There is probably a PAPIPlaceholder that could not be parsed." : "." +
					" You should attempt to correct this in your towny\\settings\\chatconfig.yml file and use /townychat reload.");
			Chat.getTownyChat().getLogger().severe(errmsg);

			if (percentSymbol)
				// Attempt to remove the unparsed placeholder and send this right back.
				catchFormatConversionException(event, format, purgePAPI(newFormat, "%" + e.getConversion()));
			else
				// Just let the chat go, this results in an error in the log, and TownyChat not being able to format chat.
				event.setFormat(format);
		}
	}

	private String purgePAPI(String format, String startOfPlaceholder) {
		return format.replaceAll(startOfPlaceholder + ".*%", "");
	}

	/**
	 * Send off TownyChat's {@link AsyncChatHookEvent} which allows other plugins to
	 * cancel or modify TownyChat's messaging.
	 * 
	 * @param event {@link AsyncPlayerChatEvent} that has caused a message.
	 * @param channelType {@link channelTypes} which this message is being sent through.
	 * @return false if the AsyncChatHookEvent is cancelled.
	 */
	private boolean sendOffHookedMessage(AsyncPlayerChatEvent event, channelTypes channelType) {
		AsyncChatHookEvent hookEvent = new AsyncChatHookEvent(event, this, !Bukkit.getServer().isPrimaryThread());
		Bukkit.getServer().getPluginManager().callEvent(hookEvent);
		if (hookEvent.isCancelled())
			return false;
		/*
		 * Send spy message before another plugin changes any of the recipients, so we
		 * know which people can see it.
		 */
		sendSpyMessage(event, channelType);

		if (hookEvent.isChanged()) {
			event.setMessage(hookEvent.getMessage());
			event.setFormat(hookEvent.getFormat());
			event.getRecipients().clear();
			event.getRecipients().addAll(hookEvent.getRecipients());
		}
		return true;
	}

	/**
	 * Sends messages to spies who have not already seen the message naturally.
	 * 
	 * @param event - Chat Event.
	 * @param type - Channel Type
	 */
	private void sendSpyMessage(AsyncPlayerChatEvent event, channelTypes type) {
		Set<Player> recipients = event.getRecipients();
		Set<Player> spies = getSpies();
		String format = formatSpyMessage(type, event.getPlayer());
		if (format == null) return;
		
		String message = Colors.translateColorCodes(event.getMessage());
		// Remove spies who've already seen the message naturally.
		spies.stream()
			.filter(spy -> !recipients.contains(spy))
			.forEach(spy -> TownyMessaging.sendMessage(spy, format + message));
	}

	/**
	 * @return A Set of online players who are spying.
	 */
	private Set<Player> getSpies() {
		// Compile the list of recipients with spy perms
		return plugin.getServer().getOnlinePlayers().stream()
				.filter(p -> plugin.getTowny().hasPlayerMode(p, "spy"))
				.collect(Collectors.toSet());
	}

	/**
	 * Formats look of message for spies
	 * 
	 * @param type - Channel Type.
	 * @param player - Player who chatted.
	 * @return format - Message format.
	 */
	@Nullable
	private String formatSpyMessage(channelTypes type, Player player) {
		Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
		if (resident == null)
			return null;
		String channelPrefix = Colors.translateColorCodes(getChannelTag() != null ? getChannelTag() : getName()) + " ";
		if (isGovernmentChannel()) // Town, Nation, Alliance channels get an extra [Name] added after the channelPrefix.
			channelPrefix = getGovtChannelSpyingPrefix(resident, type, channelPrefix);
		return ChatColor.GOLD + "[SPY] " + ChatColor.WHITE + channelPrefix + resident.getName() + ": ";
	}

	private String getGovtChannelSpyingPrefix(Resident resident, channelTypes type, String channelPrefix) {
		String slug = type.equals(channelTypes.TOWN)
			? TownyAPI.getInstance().getResidentTownOrNull(resident).getName()    // Town chat.
			: TownyAPI.getInstance().getResidentNationOrNull(resident).getName(); // Nation or Alliance chat.
		return channelPrefix + "[" + slug + "] ";
	}

	/**
	 * Try to send a channel sound, if enabled.
	 * 
	 * @param recipients Set of Players that will receive the message and potentially the sound.
	 */
	private void tryPlayChannelSound(Set<Player> recipients) {
		if (getChannelSound() == null)
			return;
		for (Player recipient : recipients) {
			if (!isSoundMuted(recipient)) {
				try {
					recipient.playSound(recipient, Sound.valueOf(getChannelSound()), 1.0f, 1.0f);
				} catch (IllegalArgumentException ex) {
					plugin.getLogger().warning("Channel " + this.getName() + " has an invalid sound configured.");
					setChannelSound(null);
					break;
				}
			}
		}
	}

	/**
	 * Try to send a message to dynmap's web chat.
	 * @param player Player which has spoken.
	 * @param message Message being spoken.
	 */
	private void tryPostToDynmap(Player player, String message) {
		if (super.getRange() > 0)
			return;
		DynmapAPI dynMap = plugin.getDynmap();
		if (dynMap != null)
			dynMap.postPlayerMessageToWeb(player, message);
	}
}
