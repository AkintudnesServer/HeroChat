package com.herocraftonline.dthielke.herochat;

import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;

public class HeroChatServerListener extends ServerListener {

    private HeroChat plugin;
    
    public HeroChatServerListener(HeroChat plugin) {
        this.plugin = plugin;
    }
    
    public void onPluginEnable(PluginEnableEvent event) {
        Plugin plugin = event.getPlugin();
        String name = plugin.getDescription().getName();
        
        if (name.equals("Permissions")) {
            this.plugin.loadPermissions();
        } else if (name.equals("CraftIRC")) {
            this.plugin.loadCraftIRC();
        } else if (name.equals("Multiverse-Core")) {
            this.plugin.loadMultiverse();
        } else if (name.equals("iChat")) {
            this.plugin.issueConflictWarning(plugin);
        } else if (name.equals("EssentialsChat")) {
            this.plugin.issueConflictWarning(plugin);
        }
    }

}
