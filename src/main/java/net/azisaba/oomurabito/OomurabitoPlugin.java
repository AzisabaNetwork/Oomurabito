package net.azisaba.oomurabito;

import org.bukkit.plugin.java.JavaPlugin;

public final class OomurabitoPlugin extends JavaPlugin {
    private BossConfig bossConfig;
    private BossManager bossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadBossConfig();

        bossManager = new BossManager(this);
        getServer().getPluginManager().registerEvents(bossManager, this);

        OomurabitoCommand command = new OomurabitoCommand(this, bossManager);
        getCommand("oomurabito").setExecutor(command);
        getCommand("oomurabito").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdown();
        }
    }

    public void reloadBossConfig() {
        reloadConfig();
        bossConfig = BossConfig.from(getConfig());
    }

    public BossConfig bossConfig() {
        return bossConfig;
    }
}
