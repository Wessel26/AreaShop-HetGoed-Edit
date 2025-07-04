package me.wiefferink.areashop.handlers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.interfaces.GeneralRegionInterface;
import me.wiefferink.areashop.interfaces.WorldEditSelection;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bukkit.entity.Player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class WorldEditHandler {
	protected final AreaShop plugin;
	private final WorldEditPlugin worldEdit;

	public WorldEditHandler(AreaShop plugin, WorldEditPlugin worldEdit) {
		this.plugin = plugin;
		this.worldEdit = worldEdit;
	}

	/**
	 * Get the selection of the player.
	 *
	 * @param player Player to get the selection for
	 * @return WorldEditSelection if the player has selected something, otherwise null
	 */
	public WorldEditSelection getPlayerSelection(Player player) {
		try {
			Region region = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
			return new WorldEditSelection(
					player.getWorld(),
					BukkitAdapter.adapt(player.getWorld(), region.getMinimumPoint()),
					BukkitAdapter.adapt(player.getWorld(), region.getMaximumPoint())
			);
		} catch (IncompleteRegionException e) {
			return null;
		}
	}

	/**
	 * Loads the contents of a region from a schematic
	 *
	 * @param rawFile            File to try restoring from to the location of the region
	 * @param regionInterface Region to restore from
	 * @return true when successful, otherwise false
	 */
	public boolean restoreRegionBlocks(File rawFile, GeneralRegionInterface regionInterface) {
		File file = null;
		ClipboardFormat format = null;
		for (ClipboardFormat formatOption : ClipboardFormats.getAll()) {
			for (String extension : formatOption.getFileExtensions()) {
				File fileOption = new File(rawFile.getAbsolutePath() + "." + extension);
				if (fileOption.exists() && formatOption.isFormat(fileOption)) {
					file = fileOption;
					format = formatOption;
				}
			}
		}
		if(file == null) {
			plugin.getLogger().info("Did not restore region " + regionInterface.getName() + ", schematic file does not exist: " + rawFile.getAbsolutePath());
			return false;
		}
		plugin.debugI("Trying to restore region", regionInterface.getName(), "from file", file.getAbsolutePath(), "with format", format.getName());

		com.sk89q.worldedit.world.World world = null;
		if(regionInterface.getName() != null) {
			world = BukkitAdapter.adapt(regionInterface.getWorld());
		}
		if(world == null) {
			plugin.getLogger().info("Did not restore region " + regionInterface.getName() + ", world not found: " + regionInterface.getWorldName());
			return false;
		}

		EditSession editSession = worldEdit.getWorldEdit()
				.newEditSessionBuilder()
				.world(world)
				.maxBlocks(plugin.getConfig().getInt("maximumBlocks"))
				.build();

		ProtectedRegion region = regionInterface.getRegion();
		// Get the origin and size of the region
		BlockVector3 origin = BlockVector3.at(region.getMinimumPoint().x(), region.getMinimumPoint().y(), region.getMinimumPoint().z());

		// Read the schematic and paste it into the world
		try(Closer closer = Closer.create()) {
			FileInputStream fis = closer.register(new FileInputStream(file));
			BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
			ClipboardReader reader = format.getReader(bis);

			//WorldData worldData = world.getWorldData();
			LocalSession session = new LocalSession(worldEdit.getLocalConfiguration());
			Clipboard clipboard = reader.read();
			if(clipboard.getDimensions().y() != regionInterface.getHeight()
					|| clipboard.getDimensions().x() != regionInterface.getWidth()
					|| clipboard.getDimensions().z() != regionInterface.getDepth()) {
				plugin.getLogger().warning("Size of the region " + regionInterface.getName() + " is not the same as the schematic to restore!");
				plugin.debugI("schematic|region, x:" + clipboard.getDimensions().x() + "|" + regionInterface.getWidth() + ", y:" + clipboard.getDimensions().y() + "|" + regionInterface.getHeight() + ", z:" + clipboard.getDimensions().z() + "|" + regionInterface.getDepth());
			}
			clipboard.setOrigin(clipboard.getMinimumPoint());
			ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
			session.setBlockChangeLimit(plugin.getConfig().getInt("maximumBlocks"));
			session.setClipboard(clipboardHolder);

			// Build operation
			BlockTransformExtent extent = new BlockTransformExtent(clipboardHolder.getClipboard(), clipboardHolder.getTransform());
			ForwardExtentCopy copy = new ForwardExtentCopy(extent, clipboard.getRegion(), clipboard.getOrigin(), editSession, origin);
			copy.setCopyingEntities(false);
			copy.setTransform(clipboardHolder.getTransform());
			// Mask to region (for polygon and other weird shaped regions)
			// TODO make this more efficient (especially for polygon regions)
			if(region.getType() != RegionType.CUBOID) {
				copy.setSourceMask(new Mask() {
					@Override
					public boolean test(BlockVector3 vector) {
						return region.contains(vector);
					}

					@Override
					public Mask2D toMask2D() {
						return null;
					}
				});
			}
			Operations.completeLegacy(copy);
		} catch(MaxChangedBlocksException e) {
			plugin.getLogger().warning("exceeded the block limit while restoring schematic of " + regionInterface.getName() + ", limit in exception: " + e.getBlockLimit() + ", limit passed by AreaShop: " + plugin.getConfig().getInt("maximumBlocks"));
			return false;
		} catch(IOException e) {
			plugin.getLogger().warning("An error occured while restoring schematic of " + regionInterface.getName() + ", enable debug to see the complete stacktrace");
			plugin.debugI(ExceptionUtils.getStackTrace(e));
			return false;
		} catch (Exception e) {
			plugin.getLogger().warning("crashed during restore of " + regionInterface.getName());
			plugin.debugI(ExceptionUtils.getStackTrace(e));
			return false;
		}
		editSession.close();
		return true;
	}

	/**
	 * Saves the contents of a region to a schematic
	 *
	 * @param file            File to try saving the region to
	 * @param regionInterface Region to restore from
	 * @return true when successful, otherwise false
	 */
	public boolean saveRegionBlocks(File file, GeneralRegionInterface regionInterface) {
		ClipboardFormat format = ClipboardFormats.findByAlias("sponge.3");
		if(format == null) {
			// Sponge format does not exist, try to select another one
			for(ClipboardFormat otherFormat : ClipboardFormats.getAll()) {
				format = otherFormat;
			}
			if(format == null) {
				plugin.getLogger().warning("Cannot find a format to save a schematic in, no available formats!");
				return false;
			}
		}

		file = new File(file.getAbsolutePath() + "." + format.getPrimaryFileExtension());
		plugin.debugI("Trying to save region", regionInterface.getName(), " to file", file.getAbsolutePath(), "with format", format.getName());
		com.sk89q.worldedit.world.World world = null;
		if(regionInterface.getWorld() != null) {
			world = BukkitAdapter.adapt(regionInterface.getWorld());
		}
		if(world == null) {
			plugin.getLogger().warning("Did not save region " + regionInterface.getName() + ", world not found: " + regionInterface.getWorldName());
			return false;
		}

		EditSession editSession = worldEdit.getWorldEdit()
				.newEditSessionBuilder()
				.world(world)
				.maxBlocks(plugin.getConfig().getInt("maximumBlocks"))
				.build();

		// Create a clipboard
		CuboidRegion selection = new CuboidRegion(world, regionInterface.getRegion().getMinimumPoint(), regionInterface.getRegion().getMaximumPoint());
		BlockArrayClipboard clipboard = new BlockArrayClipboard(selection);
		clipboard.setOrigin(regionInterface.getRegion().getMinimumPoint());
		ForwardExtentCopy copy = new ForwardExtentCopy(editSession, new CuboidRegion(world, regionInterface.getRegion().getMinimumPoint(), regionInterface.getRegion().getMaximumPoint()), clipboard, regionInterface.getRegion().getMinimumPoint());
		try {
			Operations.completeLegacy(copy);
		} catch(MaxChangedBlocksException e) {
			plugin.getLogger().warning("Exceeded the block limit while saving schematic of " + regionInterface.getName() + ", limit in exception: " + e.getBlockLimit() + ", limit passed by AreaShop: " + plugin.getConfig().getInt("maximumBlocks"));
			return false;
		}

		try(Closer closer = Closer.create()) {
			FileOutputStream fos = closer.register(new FileOutputStream(file));
			BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
			ClipboardWriter writer = closer.register(format.getWriter(bos));
			writer.write(clipboard);
		} catch(IOException e) {
			plugin.getLogger().warning("An error occured while saving schematic of " + regionInterface.getName() + ", enable debug to see the complete stacktrace");
			plugin.debugI(ExceptionUtils.getStackTrace(e));
			return false;
		} catch (Exception e) {
			plugin.getLogger().warning("crashed during save of " + regionInterface.getName());
			plugin.debugI(ExceptionUtils.getStackTrace(e));
			return false;
		}
		return true;
	}
}






















