/**
 * Copyright (C) 2011 DThielke <dave.thielke@gmail.com>
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/3.0/ or send a letter to
 * Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 **/

package com.herocraftonline.dthielke.herochat.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

import com.herocraftonline.dthielke.herochat.HeroChat;
import com.herocraftonline.dthielke.herochat.HeroChat.ChatColor;
import com.herocraftonline.dthielke.herochat.channels.Channel;
import com.herocraftonline.dthielke.herochat.channels.ChannelManager;
import com.herocraftonline.dthielke.herochat.channels.LocalChannel;

public class ConfigManager {
    protected HeroChat plugin;
    protected File primaryConfigFile;
    protected File usersConfigFolder;

    public ConfigManager(HeroChat plugin) {
        this.plugin = plugin;
        this.primaryConfigFile = new File(plugin.getDataFolder(), "config.yml");
        this.usersConfigFolder = new File(plugin.getDataFolder(), "users/");
        usersConfigFolder.mkdirs();
    }

    public void reload() throws Exception {
        load();
    }

    public void load() throws Exception {
        checkConfig();

        Configuration config = new Configuration(primaryConfigFile);
        config.load();
        loadChannels(config);
        loadGlobals(config);
    }

    private void checkConfig() {
        if (!primaryConfigFile.exists()) {
            try {
                primaryConfigFile.getParentFile().mkdir();
                primaryConfigFile.createNewFile();
                OutputStream output = new FileOutputStream(primaryConfigFile, false);
                InputStream input = ConfigManager.class.getResourceAsStream("config.yml");
                byte[] buf = new byte[8192];
                while (true) {
                    int length = input.read(buf);
                    if (length < 0) {
                        break;
                    }
                    output.write(buf, 0, length);
                }
                input.close();
                output.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadGlobals(Configuration config) {
        String globals = "globals.";
        ChannelManager cm = plugin.getChannelManager();
        String pluginTag = config.getString(globals + "plugin-tag", "[HeroChat] ").replace("&", "§");
        String ircTag = config.getString(globals + "craftIRC-prefix", "#");
        String ircMessageFormat = config.getString(globals + "craftIRC-message-format", "[{nick}] {player}: ");
        String defaultChannel = config.getString(globals + "default-channel", cm.getChannels().get(0).getName());
        String defaultMsgFormat = config.getString(globals + "default-message-format", "{player}: ");
        String incomingTellFormat = config.getString(globals + "incoming-tell-format", "{prefix}{player} &8->&d ");
        String outgoingTellFormat = config.getString(globals + "outgoing-tell-format", "{prefix}{player} &8->&d "); 
        List<String> censors = config.getStringList(globals + "censors", null);
        boolean separateChatLog = config.getBoolean(globals + "separate-chat-log", false);

        plugin.setTag(pluginTag);
        plugin.setIrcTag(ircTag);
        plugin.setIrcMessageFormat(ircMessageFormat);
        plugin.setCensors(censors);
        plugin.setIncomingTellFormat(incomingTellFormat);
        plugin.setOutgoingTellFormat(outgoingTellFormat);
        cm.setDefaultChannel(cm.getChannel(defaultChannel));
        cm.setDefaultMsgFormat(defaultMsgFormat);
        plugin.setSeparateChatLog(separateChatLog);
    }

    private void loadChannels(Configuration config) {
        List<Channel> list = new ArrayList<Channel>();
        for (String s : config.getKeys("channels")) {
            String root = "channels." + s + ".";
            Channel c;
            if (config.getBoolean(root + "options.local", false)) {
                c = new LocalChannel(plugin);
                ((LocalChannel) c).setDistance(config.getInt(root + "local-distance", 100));
            } else {
                c = new Channel(plugin);
            }

            c.setName(s);
            c.setNick(config.getString(root + "nickname", "DEFAULT-NICK"));
            c.setPassword(config.getString(root + "password", ""));
            c.setColor(ChatColor.valueOf(config.getString(root + "color", "WHITE")));
            c.setNickColor(ChatColor.valueOf(config.getString(root + "nickcol", "WHITE")));
            c.setMsgFormat(config.getString(root + "message-format", "{default}"));
            c.setWorlds(config.getStringList(root + "worlds", null));
            
            String craftIRC = root + "craftIRC.";
            c.setIRCToGameTags(config.getStringList(craftIRC + "IRC-to-game", null));
            c.setGameToIRCTags(config.getStringList(craftIRC + "game-to-IRC", null));

            String options = root + "options.";
            c.setVerbose(config.getBoolean(options + "join-messages", true));
            c.setQuickMessagable(config.getBoolean(options + "shortcut-allowed", false));
            c.setHidden(config.getBoolean(options + "hidden", false));
            c.setAutoJoined(config.getBoolean(options + "auto-join", false));
            c.setForced(config.getBoolean(options + "forced", false));
            c.setCrossWorld(config.getBoolean(options + "cross-world-chat", true));

            String lists = root + "lists.";
            c.setBlacklist(config.getStringList(lists + "bans", null));
            c.setModerators(config.getStringList(lists + "moderators", null));

            String permissions = root + "permissions.";
            c.setWhitelist(config.getStringList(permissions + "join", null));
            c.setVoicelist(config.getStringList(permissions + "speak", null));

            list.add(c);
        }
        plugin.getChannelManager().setChannels(list);
    }

    public void loadPlayer(String name) {
        File userConfigFile = new File(usersConfigFolder, name + ".yml");
        try {
            Configuration config = new Configuration(userConfigFile);
            config.load();
            ChannelManager channelManager = plugin.getChannelManager();
            try {
                String activeChannelName = config.getString("active-channel", channelManager.getDefaultChannel().getName());
                Channel activeChannel = channelManager.getChannel(activeChannelName);
                if (activeChannel != null) {
                    channelManager.setActiveChannel(name, activeChannelName);
                } else {
                    channelManager.setActiveChannel(name, channelManager.getDefaultChannel().getName());
                }

                List<String> joinedChannels = config.getStringList("joined-channels", null);
                if (joinedChannels.isEmpty()) {
                    channelManager.joinAutoChannels(name);
                } else {
                    for (String s : joinedChannels) {
                        Channel c = channelManager.getChannel(s);
                        if (c != null) {
                            List<String> whitelist = c.getWhitelist();
                            String group = plugin.getPermissionManager().getGroup(plugin.getServer().getPlayer(name));
                            if (!c.getBlacklist().contains(name) && (whitelist.isEmpty() || group.isEmpty() || whitelist.contains(group))) {
                                c.addPlayer(name);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                channelManager.setActiveChannel(name, channelManager.getDefaultChannel().getName());
                channelManager.joinAutoChannels(name);
                plugin.log(Level.INFO, "Loaded default settings for " + name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() throws Exception {
        Configuration config = new Configuration(primaryConfigFile);
        saveGlobals(config);
        saveChannels(config);
        config.save();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            savePlayer(player.getName());
        }
    }

    private void saveGlobals(Configuration config) throws Exception {
        ChannelManager cm = plugin.getChannelManager();
        String globals = "globals.";
        config.setProperty(globals + "plugin-tag", plugin.getTag());
        config.setProperty(globals + "craftIRC-prefix", plugin.getIrcTag());
        config.setProperty(globals + "craftIRC-message-format", plugin.getIrcMessageFormat());
        config.setProperty(globals + "incoming-tell-format", plugin.getIncomingTellFormat());
        config.setProperty(globals + "outgoing-tell-format", plugin.getOutgoingTellFormat());
        config.setProperty(globals + "default-channel", cm.getDefaultChannel().getName());
        config.setProperty(globals + "default-message-format", cm.getDefaultMsgFormat());
        config.setProperty(globals + "censors", plugin.getCensors());
        config.setProperty(globals + "separate-chat-log", plugin.hasSeparateChatLog());
    }

    private void saveChannels(Configuration config) throws Exception {
        Channel[] channels = plugin.getChannelManager().getChannels().toArray(new Channel[0]);
        for (Channel c : channels) {
            String root = "channels." + c.getName() + ".";
            config.setProperty(root + "nickname", c.getNick());
            config.setProperty(root + "password", c.getPassword());
            config.setProperty(root + "color", c.getColor().toString());
            config.setProperty(root + "nickcol", c.getNickColor().toString());
            config.setProperty(root + "message-format", c.getMsgFormat());
            config.setProperty(root + "worlds", c.getWorlds());
            if (c instanceof LocalChannel) {
                config.setProperty(root + "local-distance", ((LocalChannel) c).getDistance());
            }
            
            String craftIRC = root + "craftIRC.";
            config.setProperty(craftIRC + "IRC-to-game", c.getIRCToGameTags());
            config.setProperty(craftIRC + "game-to-IRC", c.getGameToIRCTags());

            String options = root + "options.";
            config.setProperty(options + "join-messages", c.isVerbose());
            config.setProperty(options + "shortcut-allowed", c.isQuickMessagable());
            config.setProperty(options + "hidden", c.isHidden());
            config.setProperty(options + "auto-join", c.isAutoJoined());
            config.setProperty(options + "local", c instanceof LocalChannel);
            config.setProperty(options + "forced", c.isForced());
            config.setProperty(options + "cross-world-chat", c.isCrossWorld());

            String lists = root + "lists.";
            config.setProperty(lists + "bans", c.getBlacklist());
            config.setProperty(lists + "moderators", c.getModerators());

            String permissions = root + "permissions.";
            config.setProperty(permissions + "join", c.getWhitelist());
            config.setProperty(permissions + "speak", c.getVoicelist());
        }
    }

    public void savePlayer(String name) {
        File userConfigFile = new File(usersConfigFolder, name + ".yml");
        try {
            Configuration config = new Configuration(userConfigFile);
            ChannelManager configManager = plugin.getChannelManager();
            Channel active = configManager.getActiveChannel(name);
            List<Channel> joinedChannels = configManager.getJoinedChannels(name);
            List<String> joinedChannelNames = new ArrayList<String>();
            for (Channel channel : joinedChannels) {
                joinedChannelNames.add(channel.getName());
            }
            config.setProperty("active-channel", active.getName());
            config.setProperty("joined-channels", joinedChannelNames);
            config.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
