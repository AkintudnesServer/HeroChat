/**
 * Copyright (C) 2011 DThielke <dave.thielke@gmail.com>
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/3.0/ or send a letter to
 * Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 **/

package com.herocraftonline.dthielke.herochat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.ensifera.animosity.craftirc.CraftIRC;
import com.herocraftonline.dthielke.herochat.channels.Channel;
import com.herocraftonline.dthielke.herochat.channels.ChannelManager;
import com.herocraftonline.dthielke.herochat.channels.ConversationManager;
import com.herocraftonline.dthielke.herochat.command.CommandManager;
import com.herocraftonline.dthielke.herochat.command.commands.BanCommand;
import com.herocraftonline.dthielke.herochat.command.commands.CreateCommand;
import com.herocraftonline.dthielke.herochat.command.commands.FocusCommand;
import com.herocraftonline.dthielke.herochat.command.commands.GMuteCommand;
import com.herocraftonline.dthielke.herochat.command.commands.HelpCommand;
import com.herocraftonline.dthielke.herochat.command.commands.IgnoreCommand;
import com.herocraftonline.dthielke.herochat.command.commands.JoinCommand;
import com.herocraftonline.dthielke.herochat.command.commands.KickCommand;
import com.herocraftonline.dthielke.herochat.command.commands.LeaveCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ListCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ModCommand;
import com.herocraftonline.dthielke.herochat.command.commands.MuteCommand;
import com.herocraftonline.dthielke.herochat.command.commands.QuickMsgCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ReloadCommand;
import com.herocraftonline.dthielke.herochat.command.commands.RemoveCommand;
import com.herocraftonline.dthielke.herochat.command.commands.TellCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ToggleCommand;
import com.herocraftonline.dthielke.herochat.command.commands.WhoCommand;
import com.herocraftonline.dthielke.herochat.util.ConfigManager;
import com.herocraftonline.dthielke.herochat.util.PermissionManager;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.onarandombox.MultiverseCore.MultiverseCore;

public class HeroChat extends JavaPlugin {

    public enum ChatColor {
        BLACK("§0"),
        NAVY("§1"),
        GREEN("§2"),
        BLUE("§3"),
        RED("§4"),
        PURPLE("§5"),
        GOLD("§6"),
        LIGHT_GRAY("§7"),
        GRAY("§8"),
        DARK_PURPLE("§9"),
        LIGHT_GREEN("§a"),
        LIGHT_BLUE("§b"),
        ROSE("§c"),
        LIGHT_PURPLE("§d"),
        YELLOW("§e"),
        WHITE("§f");

        public final String str;

        ChatColor(String str) {
            this.str = str;
        }
    }

    private static Logger log = Logger.getLogger("Minecraft");
    private static Logger chatLog = Logger.getLogger("HeroChat");
    private boolean separateChatLog;
    private ChannelManager channelManager;
    private CommandManager commandManager;
    private ConversationManager conversationManager;
    private ConfigManager configManager;
    private PermissionManager permissionManager;
    private CraftIRC craftIRC;
    private String ircMessageFormat;
    private String ircTag;
    private String tag;
    private String outgoingTellFormat;
    private String incomingTellFormat;
    private List<String> censors;
    private HeroChatServerListener serverListener;
    private HeroChatPlayerListener playerListener;
    private HeroChatCraftIRCListener craftIRCListener;
    private boolean eventsRegistered = false;
    private static MultiverseCore multiverseCore = null;

    public void onDisable() {
        try {
            for (Player player : getServer().getOnlinePlayers()) {
                configManager.savePlayer(player.getName());
            }
            configManager.save();
        } catch (Exception e) {}
        PluginDescriptionFile desc = getDescription();
        log(Level.INFO, desc.getName() + " version " + desc.getVersion() + " disabled.");
    }
    
    public static MultiverseCore getMultiverseCore() {
        return multiverseCore;
    }

    public void onEnable() {
        channelManager = new ChannelManager(this);
        conversationManager = new ConversationManager();
        permissionManager = new PermissionManager(null);
        registerEvents();
        registerCommands();

        try {
            configManager = new ConfigManager(this);
            configManager.load();
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.WARNING, "Error encountered while loading data. Check your config.yml and users.yml. Disabling HeroChat.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (Player player : getServer().getOnlinePlayers()) {
            playerListener.onPlayerJoin(new PlayerJoinEvent(player, ""));
        }

        try {
            configManager.save();
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.WARNING, "Error encountered while saving data. Disabling HeroChat.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        if (separateChatLog) {
            separateChatLog();
        }
        
        PluginDescriptionFile desc = getDescription();
        log(Level.INFO, desc.getName() + " version " + desc.getVersion() + " enabled.");

        loadPermissions();
        loadCraftIRC();
        loadMultiverse();
        checkConflict("iChat");
        checkConflict("EssentialsChat");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commandManager.dispatch(sender, command, label, args);
    }

    private void registerEvents() {
        if (!eventsRegistered) {
            playerListener = new HeroChatPlayerListener(this);
            serverListener = new HeroChatServerListener(this);
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.registerEvent(Type.PLAYER_CHAT, playerListener, Priority.High, this);
            pluginManager.registerEvent(Type.PLAYER_JOIN, playerListener, Priority.Normal, this);
            pluginManager.registerEvent(Type.PLAYER_QUIT, playerListener, Priority.Normal, this);
            pluginManager.registerEvent(Type.PLAYER_COMMAND_PREPROCESS, playerListener, Priority.Normal, this);
            pluginManager.registerEvent(Type.PLUGIN_ENABLE, serverListener, Priority.Normal, this);
            eventsRegistered = true;
        }
    }

    private void registerCommands() {
        commandManager = new CommandManager();
        // page 1
        commandManager.addCommand(new ListCommand(this));
        commandManager.addCommand(new WhoCommand(this));
        commandManager.addCommand(new FocusCommand(this));
        commandManager.addCommand(new JoinCommand(this));
        commandManager.addCommand(new LeaveCommand(this));
        commandManager.addCommand(new QuickMsgCommand(this));
        commandManager.addCommand(new IgnoreCommand(this));
        commandManager.addCommand(new TellCommand(this));
        // page 2
        commandManager.addCommand(new CreateCommand(this));
        commandManager.addCommand(new RemoveCommand(this));
        commandManager.addCommand(new ModCommand(this));
        commandManager.addCommand(new KickCommand(this));
        commandManager.addCommand(new BanCommand(this));
        commandManager.addCommand(new MuteCommand(this));
        commandManager.addCommand(new GMuteCommand(this));
        commandManager.addCommand(new ReloadCommand(this));
        // page 3
        commandManager.addCommand(new ToggleCommand(this));
        commandManager.addCommand(new HelpCommand(this));
    }

    public void loadPermissions() {
        Plugin plugin = this.getServer().getPluginManager().getPlugin("Permissions");
        if (plugin != null) {
            if (plugin.isEnabled()) {
                Permissions permissions = (Permissions) plugin;
                PermissionHandler security = permissions.getHandler();
                PermissionManager ph = new PermissionManager(security);
                this.permissionManager = ph;
                log(Level.INFO, "Permissions " + permissions.getDescription().getVersion() + " found.");

                for (Player player : getServer().getOnlinePlayers()) {
                    String name = player.getName();
                    String group = permissionManager.getGroup(player);
                    List<Channel> joinedChannels = channelManager.getJoinedChannels(name);
                    for (Channel channel : joinedChannels) {
                        if (!group.isEmpty() && !channel.getWhitelist().contains(group) && !channel.getWhitelist().isEmpty()) {
                            channel.removePlayer(name);
                        }
                    }
                }
            }
        }
    }

    public void loadCraftIRC() {
        Plugin plugin = this.getServer().getPluginManager().getPlugin("CraftIRC");
        if (plugin != null) {
            if (plugin.isEnabled()) {
                try {
                    craftIRC = (CraftIRC) plugin;
                    craftIRCListener = new HeroChatCraftIRCListener(this);
                    this.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, craftIRCListener, Event.Priority.Normal, this);
                    log(Level.INFO, "CraftIRC " + craftIRC.getDescription().getVersion() + " found.");
                } catch (ClassCastException ex) {
                    ex.printStackTrace();
                    log(Level.WARNING, "Error encountered while connecting to CraftIRC!");
                    craftIRC = null;
                    craftIRCListener = null;
                }
            }
        }
    }

    public void loadMultiverse() {
        Plugin plugin = this.getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (plugin != null) {
            if (plugin.isEnabled()) {
                try {
                    multiverseCore = (MultiverseCore) plugin;
                    log(Level.INFO, "Multiverse-Core " + multiverseCore.getDescription().getVersion() + " found.");
                } catch (ClassCastException ex) {
                    ex.printStackTrace();
                    log(Level.WARNING, "Error encountered while integrating with MultiverseCore!");
                    multiverseCore = null;
                }
            }
        }
    }

    private void checkConflict(String pluginName) {
        Plugin plugin = this.getServer().getPluginManager().getPlugin(pluginName);
        if (plugin != null) {
            if (plugin.isEnabled()) {
                issueConflictWarning(plugin);
            }
        }
    }

    public void issueConflictWarning(Plugin conflict) {
        log(Level.WARNING, "Conflicting plugin detected: " + conflict.getDescription().getName() + ". If you experience issues, please try disabling this plugin.");
    }
    
    private void separateChatLog() {
        try {
            chatLog.setUseParentHandlers(false);
            FileHandler fh = new FileHandler(getDataFolder().getAbsolutePath() + "/chat.log", true);
            fh.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return new SimpleDateFormat("HH:mm:ss").format(new Date(record.getMillis())) + " " + record.getMessage() + "\n";
                }
            });
            chatLog.addHandler(fh);
        } catch (SecurityException e1) {
            chatLog.setUseParentHandlers(true);
        } catch (IOException e1) {
            chatLog.setUseParentHandlers(true);
        }
    }

    public void log(Level level, String msg) {
        log.log(level, "[HeroChat] " + msg.replaceAll("§[0-9a-f]", ""));
    }

    public void logChat(String msg) {
        chatLog.info(msg.replaceAll("§[0-9a-f]", ""));
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CraftIRC getCraftIRC() {
        return craftIRC == null ? null : craftIRC;
    }

    public void setIrcTag(String ircTag) {
        this.ircTag = ircTag;
    }

    public String getIrcTag() {
        return ircTag;
    }

    public void setIrcMessageFormat(String ircMessageFormat) {
        this.ircMessageFormat = ircMessageFormat;
    }

    public String getIrcMessageFormat() {
        return ircMessageFormat;
    }

    public void setCensors(List<String> censors) {
        this.censors = censors;
    }

    public List<String> getCensors() {
        return censors;
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public void setOutgoingTellFormat(String outgoingTellFormat) {
        this.outgoingTellFormat = outgoingTellFormat;
    }

    public String getOutgoingTellFormat() {
        return outgoingTellFormat;
    }

    public void setIncomingTellFormat(String incomingTellFormat) {
        this.incomingTellFormat = incomingTellFormat;
    }

    public String getIncomingTellFormat() {
        return incomingTellFormat;
    }

    public void setSeparateChatLog(boolean separateChatLog) {
        this.separateChatLog = separateChatLog;
    }
    
    public boolean hasSeparateChatLog() {
        return separateChatLog;
    }
}
