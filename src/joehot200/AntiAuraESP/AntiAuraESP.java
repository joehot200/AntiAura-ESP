package joehot200.AntiAuraESP;

import joehot200.AntiAuraESP.esp.EntityHidingA;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiAuraESP extends JavaPlugin {

    /*
        Wow this class is so big
     */

    public static AntiAuraESP instance;
    @Override
    public void onEnable(){
        instance = this;
        new EntityHidingA(this);
    }

}