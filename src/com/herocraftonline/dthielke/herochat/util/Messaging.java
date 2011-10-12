/**
 * Copyright (C) 2011 DThielke <dave.thielke@gmail.com>
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/3.0/ or send a letter to
 * Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 **/

package com.herocraftonline.dthielke.herochat.util;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import com.herocraftonline.dthielke.herochat.HeroChat;
import com.herocraftonline.dthielke.herochat.HeroChat.ChatColor;
import com.herocraftonline.dthielke.herochat.channels.Channel;
import com.onarandombox.MultiverseCore.MVWorld;

public class Messaging {
    private static final String[] HEALTH_COLORS = { "§0", "§4", "§6", "§e", "§2" };

    public static String format(HeroChat plugin, Channel channel, String format, String sender, String receiver, String msg, boolean sentByPlayer, boolean allowColor) {
        if (allowColor) {
            msg = msg.replaceAll("&([0-9a-f])", "§$1");
        } else {
            msg = msg.replaceAll("§[0-9a-f]", "");
        }
        List<String> censors = plugin.getCensors();
        for (String censor : censors) {
            String[] split = censor.split(";", 3);
            if (split.length == 1) {
                msg = censorMsg(plugin, sender, msg, censor, false, "", ChatColor.RED, channel.getColor());
            } else {
                if (split.length == 3) {
                    msg = censorMsg(plugin, sender, msg, split[0], true, split[1], ChatColor.valueOf(split[2]), channel.getColor());
                }
                else
                {
                    msg = censorMsg(plugin, sender, msg, split[0], true, split[1], ChatColor.RED, channel.getColor());
                }
            }
        }
        String leader = createLeader(plugin, channel, format, sender, receiver, msg, sentByPlayer);
        return leader + msg;
    }

//    private static String censorMsg(String msg, String censor, boolean customReplacement, String replacement) {
  private static String censorMsg(HeroChat plugin, String sender, String msg, String censor, boolean customReplacement, String replacement, ChatColor cencol, ChatColor chancol) {
        Pattern pattern = Pattern.compile(censor, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(msg);
        StringBuilder censoredMsg = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            plugin.log(Level.INFO, "Censored \"" + match + "\" for " + sender + "!");
            if (!customReplacement) {
                char[] replaceChars = new char[match.length()];
                Arrays.fill(replaceChars, '*');
                replacement = new String(replaceChars);
            }
            censoredMsg.append(msg.substring(0, matcher.start()) + cencol.str + replacement + chancol.str);
            msg = msg.substring(matcher.end());
            matcher = pattern.matcher(msg);
        }
        censoredMsg.append(msg);

        return censoredMsg.toString();
    }

    private static String createLeader(HeroChat plugin, Channel channel, String format, String senderName, String receiverName, String msg, boolean sentByPlayer) {
        String prefix = "";
        String suffix = "";
        String group = "";
        String groupPrefix = "";
        String groupSuffix = "";
        String world = "";
        String healthBar = "";
        if (sentByPlayer) {
            try {
                Player sender = plugin.getServer().getPlayer(senderName);
                if (sender != null) {
                    //prefix = plugin.getPermissionManager().getPrefix(sender);
                    //suffix = plugin.getPermissionManager().getSuffix(sender);
                    group = plugin.getPermissionManager().getGroup(sender);
                    //groupPrefix = plugin.getPermissionManager().getGroupPrefix(sender);
                    groupPrefix = plugin.getChannelManager().getChannel(group).getNickColor().str;
                    //groupSuffix = plugin.getPermissionManager().getGroupSuffix(sender);
                    world = getWorld(sender);
                    senderName = sender.getDisplayName();
                    healthBar = createHealthBar(sender);
                }
            } catch (Exception e) {
                e.printStackTrace();
                plugin.log(Level.WARNING, "Error encountered while fetching prefixes/suffixes from Permissions. Is Permissions properly configured and up to date?");
            }
        }

        String leader = format;
        leader = leader.replace("{default}", plugin.getChannelManager().getDefaultMsgFormat());
        leader = leader.replaceAll("&([0-9a-f])", "§$1");
        leader = leader.replace("{prefix}", prefix);
        leader = leader.replace("{suffix}", suffix);
        leader = leader.replace("{group}", group);
        leader = leader.replace("{groupprefix}", groupPrefix);
        leader = leader.replace("{groupSuffix}", groupSuffix);
        if (channel != null) {
            leader = leader.replace("{nick}", channel.getNick());
            leader = leader.replace("{name}", channel.getName());
            leader = leader.replace("{color}", channel.getColor().str);
            leader = leader.replace("{color.CHANNEL}", channel.getColor().str);
        }
        leader = leader.replace("{player}", senderName);
        leader = leader.replace("{receiver}", receiverName);
        leader = leader.replace("{healthbar}", healthBar);
        leader = leader.replace("{world}", world);
        Matcher matcher = Pattern.compile("\\{color.[a-zA-Z_]+\\}").matcher(leader);
        while (matcher.find()) {
            String match = matcher.group();
            String colorString = match.substring(7, match.length() - 1);
            leader = leader.replaceAll("\\Q" + match + "\\E", ChatColor.valueOf(colorString).str);
        }

        return leader;
    }

    private static String getWorld(Player sender) {
        if (HeroChat.getMultiverseCore() != null) {
            MVWorld world = HeroChat.getMultiverseCore().getMVWorld(sender.getWorld().getName());
            if(world != null) {
                return world.getColoredWorldString();
            }
        }
        return sender.getWorld().getName();
    }

    private static String createHealthBar(Player player) {
        int health = player.getHealth();
        if (health < 0) {
            health = 0;
        }
        int fullBars = health / 4;
        int remainder = health % 4;
        String healthBar = "";
        for (int i = 0; i < fullBars; i++) {
            healthBar += HEALTH_COLORS[4] + "|";
        }
        int barsLeft = 5 - fullBars;
        if (barsLeft > 0) {
            healthBar += HEALTH_COLORS[remainder] + "|";
            barsLeft--;
            for (int i = 0; i < barsLeft; i++) {
                healthBar += HEALTH_COLORS[0] + "|";
            }
        }
        return healthBar;
    }
}
