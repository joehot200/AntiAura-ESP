package joehot200.AntiAuraESP;

import joehot200.AntiAuraESP.esp.EntityHidingA;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiAuraESP extends JavaPlugin implements Listener {

    public static AntiAuraESP instance;
    public FileConfiguration config;
    @Override
    public void onEnable(){
        this.saveDefaultConfig();
        config = this.getConfig();
        instance = this;
        new EntityHidingA(this);
        Bukkit.getServer().getPluginManager().registerEvents(this, this);

    }




}