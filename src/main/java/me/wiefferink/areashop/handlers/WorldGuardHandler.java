package me.wiefferink.areashop.handlers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.interfaces.RegionAccessSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class WorldGuardHandler {
	protected final AreaShop plugin;

	public WorldGuardHandler(AreaShop plugin, WorldGuardPlugin worldGuard) {
		this.plugin = plugin;
	}

	/**
	 * Get the RegionManager for a certain bukkit World.
	 *
	 * @param world World to get the RegionManager for
	 * @return RegionManager if there is one for the given World
	 */
	public RegionManager getRegionManager(World world) {
		return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
	}

	/**
	 * Get a set of ProtectedRegion's that are present on a certain location.
	 *
	 * @param location The location to check
	 * @return A set containing all regions present at that location
	 */
	public Set<ProtectedRegion> getApplicableRegionsSet(Location location) {
		Set<ProtectedRegion> result = new HashSet<>();
		BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
		for(ProtectedRegion region : getRegionManager(location.getWorld()).getRegions().values()) {
			if(region.contains(vector)) {
				result.add(region);
			}
		}
		return result;
	}

	/**
	 * Build a DefaultDomain from a RegionAccessSet.
	 * @param regionAccessSet RegionAccessSet to read
	 * @return DefaultDomain containing the entities from the RegionAccessSet
	 */
	private DefaultDomain buildDomain(RegionAccessSet regionAccessSet) {
		DefaultDomain owners = new DefaultDomain();

		for(String playerName : regionAccessSet.getPlayerNames()) {
			owners.addPlayer(playerName);
		}

		for(UUID uuid : regionAccessSet.getPlayerUniqueIds()) {
			owners.addPlayer(uuid);
		}

		for(String group : regionAccessSet.getGroupNames()) {
			owners.addGroup(group);
		}

		return owners;
	}

	/**
	 * Parse an owner(s) string and set the players as owner of the WorldGuard region.
	 *
	 * @param region          The WorldGuard region to set the owners of
	 * @param regionAccessSet The owner(s) to set
	 */
	public void setOwners(ProtectedRegion region, RegionAccessSet regionAccessSet) {
		DefaultDomain defaultDomain = buildDomain(regionAccessSet);
		if(!region.getOwners().toUserFriendlyString().equals(defaultDomain.toUserFriendlyString())) {
			region.setOwners(defaultDomain);
		}
	}

	/**
	 * Parse a member(s) string and set the players as member of the WorldGuard region.
	 *
	 * @param region          The WorldGuard region to set the members of
	 * @param regionAccessSet The member(s) to set
	 */
	public void setMembers(ProtectedRegion region, RegionAccessSet regionAccessSet) {
		DefaultDomain defaultDomain = buildDomain(regionAccessSet);
		if(!region.getMembers().toUserFriendlyString().equals(defaultDomain.toUserFriendlyString())) {
			region.setMembers(defaultDomain);
		}
	}

	/**
	 * Check if a player is a member of the WorldGuard region.
	 *
	 * @param region The region to check
	 * @param player The player to check
	 * @return true if the player is a member of the region, otherwise false
	 */
	public boolean containsMember(ProtectedRegion region, UUID player) {
		return region.getMembers().contains(player);
	}

	/**
	 * Check if a player is an owner of the WorldGuard region.
	 *
	 * @param region The region to check
	 * @param player The player to check
	 * @return true if the player is an owner of the region, otherwise false
	 */
	public boolean containsOwner(ProtectedRegion region, UUID player) {
		return region.getOwners().contains(player);
	}

	/**
	 * Get the members of a region.
	 *
	 * @param region to get the members of
	 * @return RegionAccessSet with all members (by uuid and name) and groups of the given region
	 */
	public RegionAccessSet getMembers(ProtectedRegion region) {
		RegionAccessSet result = new RegionAccessSet();
		result.getGroupNames().addAll(region.getMembers().getGroups());
		result.getPlayerNames().addAll(region.getMembers().getPlayers());
		result.getPlayerUniqueIds().addAll(region.getMembers().getUniqueIds());
		return result;
	}

	/**
	 * Get the owners of a region.
	 *
	 * @param region to get the owners of
	 * @return RegionAccessSet with all owners (by uuid and name) and groups of the given region
	 */
	public RegionAccessSet getOwners(ProtectedRegion region) {
		RegionAccessSet result = new RegionAccessSet();
		result.getGroupNames().addAll(region.getOwners().getGroups());
		result.getPlayerNames().addAll(region.getOwners().getPlayers());
		result.getPlayerUniqueIds().addAll(region.getOwners().getUniqueIds());
		return result;
	}

	/**
	 * Get a flag from the name of a flag.
	 *
	 * @param flagName The name of the flag to get
	 * @return The specific flag type for the given name
	 */
	public Flag<?> fuzzyMatchFlag(String flagName) {
		return Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), flagName);
	}

	/**
	 * Convert string input to a region group flag value.
	 *
	 * @param flag  The flag to parse the input for
	 * @param input The input
	 * @param <V>   Flag type
	 * @return The RegionGroup denoted by the input
	 * @throws InvalidFlagFormat When the input for the flag is incorrect
	 */
	public <V> V parseFlagInput(Flag<V> flag, String input) throws InvalidFlagFormat {
		return flag.parseInput(FlagContext.create().setInput(input).build());
	}

	/**
	 * Convert string input to a region group flag value.
	 *
	 * @param flag  The flag to parse the input for
	 * @param input The input
	 * @return The RegionGroup denoted by the input
	 * @throws InvalidFlagFormat When the input for the flag is incorrect
	 */
	public RegionGroup parseFlagGroupInput(RegionGroupFlag flag, String input) throws InvalidFlagFormat {
		return flag.parseInput(FlagContext.create().setInput(input).build());
	}

	/**
	 * Get the minimum point of a region.
	 *
	 * @param region The region to get it for
	 * @return Minimum point represented as vector
	 */
	public Vector getMinimumPoint(ProtectedRegion region) {
		BlockVector3 min = region.getMinimumPoint();
		return new Vector(min.x(), min.y(), min.z());
	}

	/**
	 * Get the maximum point of a region.
	 *
	 * @param region The region to get it for
	 * @return Maximum point represented as vector
	 */
	public Vector getMaximumPoint(ProtectedRegion region) {
		BlockVector3 min = region.getMaximumPoint();
		return new Vector(min.x(), min.y(), min.z());
	}

	/**
	 * Get the edges of a region (meant for polygon regions).
	 *
	 * @param region The region to get it for
	 * @return Points around the edge as vector array
	 */
	public List<Vector> getRegionPoints(ProtectedRegion region) {
		List<Vector> result = new ArrayList<>();
		for (BlockVector2 point : region.getPoints()) {
			result.add(new Vector(point.x(), 0,point.z()));
		}
		return result;
	}

	/**
	 * Create a CuboidRegion.
	 *
	 * @param name Name to use in WorldEdit
	 * @param corner1  Minimum point
	 * @param corner2  Maximum point
	 * @return CuboidRegion
	 */
	public ProtectedCuboidRegion createCuboidRegion(String name, Vector corner1, Vector corner2) {
		return new ProtectedCuboidRegion(name, BlockVector3.at(corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ()), BlockVector3.at(corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ()));
	}
}
