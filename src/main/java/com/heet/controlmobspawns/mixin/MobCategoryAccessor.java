package com.heet.controlmobspawns.mixin;

import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the vanilla values, which the getters no longer return once they are configured. */
@Mixin(MobCategory.class)
public interface MobCategoryAccessor {
	@Accessor("max")
	int cms$getVanillaMax();

	@Accessor("despawnDistance")
	int cms$getVanillaDespawnDistance();

	@Accessor("noDespawnDistance")
	int cms$getVanillaNoDespawnDistance();
}
