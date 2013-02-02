package org.haunhaus.aaron.sLots.Plugin;

import java.util.Map;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class Plugin_sLots extends JavaPlugin {


    private Economy getEconomy()
    {
        Economy economy = null;
    	RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }

        if(economy != null){
        	return economy;
        }else{
        	return null;
        }
    }
	
	private WorldGuardPlugin getWorldGuard() {
    Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

	if (plugin == null || !(plugin instanceof WorldGuardPlugin)) {
	    return null;
	}
	    return (WorldGuardPlugin) plugin;
	}
	
	
	public void onEnable(){
	}
	public void onDisable(){
	}
	
	public boolean onCommand(CommandSender sender, Command c, String lbl, String[] args ){
		Location loc = null;
		ApplicableRegionSet rl = null;
		ProtectedRegion reg = null;
		RegionManager rm = null;
		EconomyResponse eres = null;
		DefaultDomain domain = new DefaultDomain();
		boolean found = false;
		WorldGuardPlugin wg = getWorldGuard();
		Map<Flag<?>,Object> flags = null;
		Player ply = null;
		if(wg == null){ //make sure that worldguard is here
			sender.sendMessage("ERROR: Could not connect to WorldGuard");
			return true;
		}
		Economy ecn = getEconomy();
		if(ecn == null){
			sender.sendMessage("ERROR: Could not connect to economy (Vault)");
			return true;
		}
		switch(c.getName().toLowerCase()){
		case "lot":
			if(!(sender instanceof Player)){ //Consoles dont have location
				sender.sendMessage("This only works for players (not the console).");
				return true;
			}
			loc = ((Player) sender).getLocation();
			rm = wg.getRegionManager(loc.getWorld());
			if(rm == null){ // check if regions are on
				sender.sendMessage("Regions are not enabled on the world!");
				return true;
			}
			rl = rm.getApplicableRegions(loc);
			found = false;
			for(ProtectedRegion r : rl){
				flags = r.getFlags();
				if(flags.containsKey(DefaultFlag.BUYABLE) & flags.containsKey(DefaultFlag.PRICE)){
					if(r.getFlag(DefaultFlag.BUYABLE) & r.getFlag(DefaultFlag.PRICE) > 0.00){ //normal lot
						sender.sendMessage("This lot is avaiable for purchase for " + r.getFlag(DefaultFlag.PRICE) +" Coins.");
						sender.sendMessage("Type \"/buylot\" to purchase.");	
						found = true;
					}else if(r.getFlag(DefaultFlag.PRICE) > 0.00 & !r.getFlag(DefaultFlag.BUYABLE)){ //special lot
						sender.sendMessage("This is a PREMIER lot, which is priced at " + r.getFlag(DefaultFlag.PRICE) + " Coins." );
						sender.sendMessage("Ask a moderator for assistance if you wish to purchase this lot.");
						sender.sendMessage("The lot's name is: \"" + r.getId() +"\".");
						found = true;
					}
				}
			}
			if(!found) sender.sendMessage("There is no lot for sale at your location.");
			return true;
		case "buylot":
			if(!(sender instanceof Player)){ //Consoles don't have location
				sender.sendMessage("This only works for players (not the console).");
				return true;
			}
			loc = ((Player) sender).getLocation();
			rm = wg.getRegionManager(loc.getWorld());
			if(rm == null){ // check if regions are on
				sender.sendMessage("Regions are not enabled on the world!");
				return true;
			}
			rl = rm.getApplicableRegions(loc);
			found = false;
			for(ProtectedRegion r : rl){
				flags=r.getFlags();
				if( flags.containsKey(DefaultFlag.BUYABLE) && flags.containsKey(DefaultFlag.PRICE)
						&& r.getFlag(DefaultFlag.BUYABLE) && r.getFlag(DefaultFlag.PRICE) > 0.00){
					reg = r;
					found = true;
				}
			}
			if(!found){
				sender.sendMessage("There is no lot for sale at your location that you may purchase.");
				return true;
			}
			if(!ecn.has(((Player) sender).getName(), reg.getFlag(DefaultFlag.PRICE))){
				sender.sendMessage("Sorry, you can't afford it.");
				return true;
			}
			eres = ecn.withdrawPlayer(((Player) sender).getName(), reg.getFlag(DefaultFlag.PRICE));
			if(!eres.transactionSuccess()){
				sender.sendMessage("Vault returned error:");
				sender.sendMessage(eres.errorMessage);
				return true;
			}
			flags = reg.getFlags(); //clear all flags
			flags.clear();
			reg.setFlags(flags);
			domain.addPlayer(((Player) sender).getName());
			reg.setOwners(domain);
			try {
				rm.save();
			} catch (ProtectionDatabaseException e) {
				e.printStackTrace();
			}
			sender.sendMessage("You are now the proud owner of lot \"" + reg.getId() +"\"");
			return true;
		case "selllot":
			if(args.length != 3) return false;
			try {
				rm = wg.getRegionManager(wg.matchWorld(sender, args[0]));
			} catch (CommandException e) {
				rm = null;
			}
			if(rm == null){ // check if regions are on
				sender.sendMessage("Regions are not enabled on the specified world!");
				return true;
			}
			reg = rm.getRegion(args[1]);
			if(reg == null){
				sender.sendMessage("Could not find the specified region!");
				return true;
			}
			flags = reg.getFlags();
			if(!(flags.containsKey(DefaultFlag.BUYABLE) && flags.containsKey(DefaultFlag.PRICE)
						&& !reg.getFlag(DefaultFlag.BUYABLE) && reg.getFlag(DefaultFlag.PRICE) > 0.00)){
				//if the region has a price and is set buyable:FALSE it can be sold a permier
				sender.sendMessage("The specified region is not a premier lot!");
				return true;
			}
			ply = Bukkit.getPlayer(args[2]);
			if(ply==null){
				sender.sendMessage("That player is not online!");
				return true;
			}
			if(!ecn.has(ply.getName(), reg.getFlag(DefaultFlag.PRICE))){
				ply.sendMessage(((Player) sender).getName() + " tried to sell you lot \"" + reg.getId() + "\" but you could not afford it.");
				sender.sendMessage(ply.getDisplayName() + " can't afford that lot.");
				return true;
			}
			ply.sendMessage(((Player) sender).getDisplayName() + " sold you lot \"" + reg.getId() + "\".");
			eres = ecn.withdrawPlayer(ply.getName(), reg.getFlag(DefaultFlag.PRICE));
			if(!eres.transactionSuccess()){
				ply.sendMessage("Something went wrong with the economy system!.");
				sender.sendMessage("Vault returned error:");
				sender.sendMessage(eres.errorMessage);
				return true;
			}
			flags = reg.getFlags(); //clear all flags
			flags.clear();
			reg.setFlags(flags);
			domain.addPlayer(ply.getName());
			reg.setOwners(domain);
			try {
				rm.save();
			} catch (ProtectionDatabaseException e) {
				e.printStackTrace();
			}
			ply.sendMessage("You are now the proud owner of lot \"" + reg.getId() +"\"");
			sender.sendMessage("You sold " + ply.getDisplayName() + " lot \"" + reg.getId() + "\"");
			return true;
		case "default":
			return false;
		}
	return false;
	}
}
