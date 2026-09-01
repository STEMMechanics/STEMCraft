package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Persistent TPS/MSPT/ping boss bar modelled after Purpur's /tpsbar command. */
public final class TpsBarFeature extends BaseFeature {
    private static final String TASK_ID="feature:tps-bar";
    private final Map<UUID,BossBar> bars=new HashMap<>();
    private NamespacedKey preferenceKey;
    private String title,fillMode;
    private BossBar.Overlay overlay;
    private long updateTicks;
    private boolean defaultEnabled;

    public TpsBarFeature(STEMCraftAPI api){super(api);}

    @Override public void onEnable(){
        preferenceKey=new NamespacedKey(STEMCraft.getPlugin(),"tps-bar-enabled");loadSettings();
        api.events().register(PlayerJoinEvent.class,event->restore(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class,event->hide(event.getPlayer()));
        api.commands().create("tpsbar").usage("/tpsbar [player]").description("Toggle the server performance boss bar.")
            .permission("stemcraft.command.tpsbar").tabCompletion("{player}")
            .executor((unused,cmd,ctx)->{
                Player target;
                if(ctx.getArg(0,null)!=null){
                    if(!ctx.hasPermission("stemcraft.command.tpsbar.other")){ctx.returnError("You cannot change another player's TPS bar.");return;}
                    target=ctx.getPlayer(0,null);if(target==null){ctx.returnError("That player is not online.");return;}
                }else{ctx.checkNotConsole();target=ctx.asPlayer();}
                boolean enabled=toggle(target);ctx.returnSuccess("TPS bar toggled "+(enabled?"on":"off")+" for "+target.getName()+".");
            }).register(STEMCraft.getPlugin());
        Bukkit.getOnlinePlayers().forEach(this::restore);startTask();
    }

    @Override public void onReload(){super.onReload();loadSettings();api.tasks().cancel(TASK_ID);startTask();}

    @Override public void onDisable(){api.tasks().cancel(TASK_ID);for(Player player:Bukkit.getOnlinePlayers())hide(player);bars.clear();}

    private void loadSettings(){
        title=getConfigSection().getString("title","<white>TPS: <tps> MSPT: <mspt> Ping: <ping>ms");
        fillMode=getConfigSection().getString("fill-mode","MSPT").toUpperCase(Locale.ROOT);
        updateTicks=Math.max(5L,getConfigSection().getLong("tick-interval",20L));
        defaultEnabled=getConfigSection().getBoolean("default-enabled",false);
        try{overlay=BossBar.Overlay.valueOf(getConfigSection().getString("overlay","NOTCHED_20").toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException ignored){overlay=BossBar.Overlay.NOTCHED_20;}
    }

    private void startTask(){api.tasks().repeating(TASK_ID,updateTicks,this::update);}

    private void update(){
        double tps=Math.min(20D,Bukkit.getTPS()[0]);double mspt=Bukkit.getAverageTickTime();
        for(Map.Entry<UUID,BossBar> entry:new HashMap<>(bars).entrySet()){
            Player player=Bukkit.getPlayer(entry.getKey());if(player==null){bars.remove(entry.getKey());continue;}
            updateBar(player,entry.getValue(),tps,mspt);
        }
    }

    private void updateBar(Player player,BossBar bar,double tps,double mspt){
        int ping=player.getPing();Status status=status(tps,mspt,ping);
        String text=title.replace("<tps>",String.format(Locale.ROOT,"%.2f",tps))
            .replace("<mspt>",String.format(Locale.ROOT,"%.2f",mspt)).replace("<ping>",Integer.toString(ping));
        String wrapper=getConfigSection().getString("text-color."+status.key,
            switch(status){case GOOD->"<gradient:#55ff55:#00aa00><text></gradient>";case MEDIUM->"<gradient:#ffff55:#ffaa00><text></gradient>";case LOW->"<gradient:#ff5555:#aa0000><text></gradient>";});
        bar.name(TextUtil.colourise(wrapper.replace("<text>",text))).progress(progress(tps,mspt,ping))
            .color(colour(status)).overlay(overlay);
    }

    private Status status(double tps,double mspt,int ping){return switch(fillMode){
        case "TPS"->tps>=19D?Status.GOOD:tps>=15D?Status.MEDIUM:Status.LOW;
        case "PING"->ping<=100?Status.GOOD:ping<=200?Status.MEDIUM:Status.LOW;
        default->mspt<=40D?Status.GOOD:mspt<=50D?Status.MEDIUM:Status.LOW;};}

    private float progress(double tps,double mspt,int ping){double value=switch(fillMode){
        case "TPS"->tps/20D;case "PING"->1D-ping/300D;default->1D-mspt/100D;};
        return(float)Math.max(0D,Math.min(1D,value));}

    private BossBar.Color colour(Status status){String fallback=switch(status){case GOOD->"GREEN";case MEDIUM->"YELLOW";case LOW->"RED";};
        try{return BossBar.Color.valueOf(getConfigSection().getString("progress-color."+status.key,fallback).toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException ignored){return BossBar.Color.valueOf(fallback);}}

    private boolean toggle(Player player){if(bars.containsKey(player.getUniqueId())){hide(player);setPreference(player,false);return false;}
        show(player);setPreference(player,true);return true;}
    private void restore(Player player){Byte saved=player.getPersistentDataContainer().get(preferenceKey,PersistentDataType.BYTE);
        if(saved==null?defaultEnabled:saved!=0)show(player);}
    private void show(Player player){if(bars.containsKey(player.getUniqueId()))return;BossBar bar=BossBar.bossBar(Component.empty(),1F,BossBar.Color.GREEN,overlay);
        updateBar(player,bar,Math.min(20D,Bukkit.getTPS()[0]),Bukkit.getAverageTickTime());
        bars.put(player.getUniqueId(),bar);player.showBossBar(bar);}
    private void hide(Player player){BossBar bar=bars.remove(player.getUniqueId());if(bar!=null)player.hideBossBar(bar);}
    private void setPreference(Player player,boolean enabled){player.getPersistentDataContainer().set(preferenceKey,PersistentDataType.BYTE,enabled?(byte)1:(byte)0);}
    private enum Status{GOOD("good"),MEDIUM("medium"),LOW("low");final String key;Status(String key){this.key=key;}}
}
