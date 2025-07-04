package me.wiefferink.areashop.handlers;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;

public class SignHandler {
	/**
	 * Get the direction a sign is facing.
	 *
	 * @param block Sign block to get the facing from
	 * @return direction the sign is facing
	 */
	public static BlockFace getSignFacing(Block block) {
		if (block == null) {
			return null;
		}

		BlockState blockState = block.getState();
		BlockData blockData = blockState.getBlockData();

		return switch (blockData) {
			case WallSign wallSign -> wallSign.getFacing();
			case Sign sign -> sign.getRotation();
			default -> null;
		};

	}

	/**
	 * Set the direction a sign is facing.
	 *
	 * @param block  Sign block to update
	 * @param facing direction to let the sign face
	 * @return true when successful, otherwise false
	 */
	public static boolean setSignFacing(Block block, BlockFace facing) {
		if (block == null || facing == null) {
			return false;
		}

		BlockState blockState = block.getState();
		BlockData blockData = blockState.getBlockData();

		switch (blockData) {
			case WallSign wallSign -> wallSign.setFacing(facing);
			case Sign sign -> sign.setRotation(facing);
			default -> {
				return false;
			}
		}

		block.setBlockData(blockData);
		return true;
	}

	/**
	 * Get the block a sign is attached to.
	 *
	 * @param block Sign block
	 * @return Block the sign is attached to, or null when not a sign or not attached
	 */
	public static Block getSignAttachedTo(Block block) {
		if (block == null) {
			return null;
		}

		BlockState blockState = block.getState();
		org.bukkit.block.data.BlockData blockData = blockState.getBlockData();

		return switch (blockData) {
			case WallSign wallSign -> block.getRelative(wallSign.getFacing().getOppositeFace());
			case Sign ignored -> block.getRelative(BlockFace.DOWN);
			default -> null;
		};

	}
}
