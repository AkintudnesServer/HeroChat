/**
 * Copyright (C) 2011 DThielke <dave.thielke@gmail.com>
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/3.0/ or send a letter to
 * Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 **/

package com.herocraftonline.dthielke.herochat.channels;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Event.Type;

import com.ensifera.animosity.craftirc.CraftIRC;
import com.herocraftonline.dthielke.herochat.HeroChat;
import com.herocraftonline.dthielke.herochat.HeroChat.ChatColor;
import com.herocraftonline.dthielke.herochat.event.ChannelChatEvent;
import com.herocraftonline.dthielke.herochat.util.Messaging;

public class Channel {

    //public static final String logFormat = "[{nick}] {player}: ";
    public static final String joinFormat = "{color}[{nick}] ";

    protected HeroChat plugin;
    protected String name;
    protected String nick;
    protected String password;
    protected String msgFormat;
    protected ChatColor color;
    protected ChatColor nickcolor;

    protected boolean enabled;
    protected boolean verbose;
    protected boolean hidden;
    protected boolean forced;
    protected boolean autoJoined;
    protected boolean quickMessagable;
    protected boolean crossWorld;

    protected List<String> players;
    protected List<String> moderators;
    protected List<String> blacklist;
    protected List<String> whitelist;
    protected List<String> voicelist;
    protected List<String> mutelist;
    protected List<String> worlds;
    protected List<String> gameToIRCTags;
    protected List<String> IRCToGameTags;

    public Channel(HeroChat plugin) {
        this.plugin = plugin;
        this.name = "DefaultName";
        this.nick = "DefaultNick";
        this.password = "";
        this.msgFormat = "{default}";
        this.color = ChatColor.WHITE;

        enabled = true;
        verbose = false;
        hidden = false;
        forced = false;
        autoJoined = false;
        quickMessagable = false;
        crossWorld = true;

        players = new ArrayList<String>();
        moderators = new ArrayList<String>();
        blacklist = new ArrayList<String>();
        whitelist = new ArrayList<String>();
        voicelist = new ArrayList<String>();
        mutelist = new ArrayList<String>();
        worlds = new ArrayList<String>();
        IRCToGameTags = new ArrayList<String>();
        gameToIRCTags = new ArrayList<String>();
    }

    public void sendMessage(String source, String msg, String format, boolean sentByPlayer) {
        sendMessage(source, msg, format, players, sentByPlayer, true);
    }

    public void sendMessage(String source, String msg, String format, boolean sentByPlayer, boolean includeSender) {
        sendMessage(source, msg, format, players, sentByPlayer, includeSender);
    }

    public void sendMessage(String source, String msg, String format, List<String> recipients, boolean sentByPlayer, boolean includeSender) {
        ChannelChatEvent event = new ChannelChatEvent(Type.CUSTOM_EVENT, this, source, msg, format, sentByPlayer);
        plugin.getServer().getPluginManager().callEvent(event);
        source = event.getSource();
        msg = event.getMessage();
        format = event.getFormat();
        sentByPlayer = event.isSentByPlayer();
        if (!event.isCancelled()) {
            if (sentByPlayer) {
                Player sender = plugin.getServer().getPlayer(source);
                if (sender != null) {
                    if (enabled || plugin.getPermissionManager().isAdmin(sender) || moderators.contains(source)) {
                        String group = plugin.getPermissionManager().getGroup(sender);
                        if (group == null || voicelist.contains(group) || voicelist.isEmpty()) {
                            if (!plugin.getChannelManager().getMutelist().contains(sender.getName())) {
                                if (!mutelist.contains(sender.getName())) {
                                    if (worlds.isEmpty() || worlds.contains(sender.getWorld().getName())) {
                                        boolean color = plugin.getPermissionManager().isAllowedColor(sender);
                                        sendUncheckedMessage(source, msg, format, sentByPlayer, players, includeSender, color);
                                    } else {
                                        sender.sendMessage(plugin.getTag() + "You are not in the correct world for " + getCName());
                                    }
                                } else {
                                    sender.sendMessage(plugin.getTag() + "You are muted in " + getCName());
                                }
                            } else {
                                sender.sendMessage(plugin.getTag() + "You are globally muted");
                            }
                        } else {
                            sender.sendMessage(plugin.getTag() + "You cannot speak in " + getCName());
                        }
                    } else {
                        sender.sendMessage(plugin.getTag() + "This channel is disabled");
                    }
                }
            } else {
                if (enabled) {
                    sendUncheckedMessage(source, msg, format, sentByPlayer, players, includeSender, true);
                }
            }
        }
    }

    public void sendMessage(String name, String msg) {
        sendMessage(name, msg, msgFormat, true);
    }

    protected void sendUncheckedMessage(String source, String msg, String format, boolean sentByPlayer, List<String> recipients, boolean includeSender, boolean color) {
        String formattedMsg = Messaging.format(plugin, this, format, source, "", msg, sentByPlayer, color);
        ChannelManager cm = plugin.getChannelManager();
        for (String recipientName : recipients) {
            if (players.contains(recipientName)) {
                if (!cm.isIgnoring(recipientName, source)) {
                    Player recipient = plugin.getServer().getPlayer(recipientName);
                    if (recipient != null) {
                        if (includeSender || !recipient.getName().equals(source)) {
                            String world = recipient.getWorld().getName();
                            if (worlds.isEmpty() || worlds.contains(world)) {
                                if (crossWorld || !sentByPlayer || (plugin.getServer().getPlayer(source).getWorld().getName().equals(world))) {
                                    recipient.sendMessage(formattedMsg);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (sentByPlayer) {
            sendIRCMessage(source, msg);
        }
        plugin.logChat(formattedMsg);
    }

    protected void sendIRCMessage(String source, String msg) {
        CraftIRC irc = plugin.getCraftIRC();
        if (irc != null) {
            String ircMsg = Messaging.format(plugin, this, plugin.getIrcMessageFormat(), source, "", msg, true, false);
            for (String tag : gameToIRCTags) {
                plugin.getCraftIRC().sendMessageToTag(ircMsg, tag);
            }
        }
    }

    public void addPlayer(String name) {
        if (!players.contains(name) && !blacklist.contains(name)) {
            players.add(name);
            if (verbose) {
                String displayName = name;
                Player p = plugin.getServer().getPlayer(name);
                if (p != null) {
                    displayName = p.getDisplayName();
                }
                String msg = "§f" + displayName + color.str + " has joined the channel";
                sendMessage(name, msg, joinFormat, players, false, false);
            }
        }
    }

    public void removePlayer(String name) {
        if (players.contains(name)) {
            players.remove(name);
            if (verbose) {
                String displayName = name;
                Player p = plugin.getServer().getPlayer(name);
                if (p != null) {
                    displayName = p.getDisplayName();
                }
                String msg = "§f" + displayName + color.str + " has left the channel";
                sendMessage(name, msg, joinFormat, players, false, false);
            }
        }
    }

    public String getCName() {
        return color.str + name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public ChatColor getNickColor() {
        return nickcolor;
    }

    public void setNickColor(ChatColor color) {
        this.nickcolor = color;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public List<String> getVoicelist() {
        return voicelist;
    }

    public void setVoicelist(List<String> voicelist) {
        this.voicelist = voicelist;
    }

    public List<String> getPlayers() {
        return players;
    }

    public String getMsgFormat() {
        return msgFormat;
    }

    public void setMsgFormat(String msgFormat) {
        this.msgFormat = msgFormat;
    }

    public List<String> getModerators() {
        return moderators;
    }

    public void setModerators(List<String> moderators) {
        this.moderators = moderators;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public boolean isAutoJoined() {
        return autoJoined;
    }

    public void setAutoJoined(boolean autoJoined) {
        this.autoJoined = autoJoined;
    }

    public boolean isQuickMessagable() {
        return quickMessagable;
    }

    public void setQuickMessagable(boolean quickMessagable) {
        this.quickMessagable = quickMessagable;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public void setWorlds(List<String> worlds) {
        this.worlds = worlds;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getMutelist() {
        return mutelist;
    }

    public void setMutelist(List<String> mutelist) {
        this.mutelist = mutelist;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCrossWorld() {
        return crossWorld;
    }

    public void setCrossWorld(boolean crossWorld) {
        this.crossWorld = crossWorld;
    }

    public List<String> getGameToIRCTags() {
        return gameToIRCTags;
    }

    public void setGameToIRCTags(List<String> gameToIRCTags) {
        this.gameToIRCTags = gameToIRCTags;
    }

    public List<String> getIRCToGameTags() {
        return IRCToGameTags;
    }

    public void setIRCToGameTags(List<String> iRCToGameTags) {
        IRCToGameTags = iRCToGameTags;
    }

}
