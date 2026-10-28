package com.heet.controlmobspawns.spawn;

/** Implemented on mobs by a mixin: true when the mob became persistent by picking up an item. */
public interface ItemPickupHolder {
	boolean cms$isPersistentFromPickup();

	void cms$setPersistentFromPickup(boolean value);
}
