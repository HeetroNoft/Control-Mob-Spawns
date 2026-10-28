package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.heet.controlmobspawns.spawn.ItemPickupHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity implements ItemPickupHolder {
	@Unique
	private static final String PICKUP_KEY = "controlmobspawns:persistent_from_pickup";

	@Shadow
	private boolean persistenceRequired;

	@Unique
	private boolean cms$persistentFromPickup;
	@Unique
	private boolean cms$wasPersistent;

	protected MobMixin(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public boolean cms$isPersistentFromPickup() {
		return cms$persistentFromPickup;
	}

	@Override
	public void cms$setPersistentFromPickup(boolean value) {
		cms$persistentFromPickup = value;
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"))
	private void cms$beforePickUp(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
		cms$wasPersistent = persistenceRequired;
	}

	@Inject(method = "pickUpItem", at = @At("TAIL"))
	private void cms$afterPickUp(ServerLevel level, ItemEntity entity, CallbackInfo ci) {
		if (!cms$wasPersistent && persistenceRequired) {
			cms$persistentFromPickup = true;
		}
	}

	@Inject(method = "setPersistenceRequired", at = @At("HEAD"))
	private void cms$explicitPersistence(CallbackInfo ci) {
		// Name tags, leads and similar: the mob is now wanted for another reason.
		cms$persistentFromPickup = false;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void cms$save(ValueOutput output, CallbackInfo ci) {
		if (cms$persistentFromPickup) {
			output.putBoolean(PICKUP_KEY, true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void cms$load(ValueInput input, CallbackInfo ci) {
		cms$persistentFromPickup = input.getBooleanOr(PICKUP_KEY, false);
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void cms$neverDespawn(CallbackInfo ci) {
		if (cms$despawnMode() == CmsConfig.DespawnMode.NEVER
			&& !(level().getDifficulty() == Difficulty.PEACEFUL && !getType().isAllowedInPeaceful())) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "checkDespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;isPersistenceRequired()Z"))
	private boolean cms$ignorePersistence(Mob mob, Operation<Boolean> original) {
		return original.call(mob) && !cms$forced() && !cms$pickupDespawnAllowed();
	}

	@WrapOperation(method = "checkDespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;removeWhenFarAway(D)Z"))
	private boolean cms$forceRemoveWhenFarAway(Mob mob, double distSqr, Operation<Boolean> original) {
		return cms$forced() || original.call(mob, distSqr);
	}

	@WrapOperation(method = "checkDespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;discard()V"))
	private void cms$dropPickedItemsOnDespawn(Mob mob, Operation<Void> original) {
		if ((cms$forced() || cms$pickupDespawnAllowed()) && level() instanceof ServerLevel serverLevel) {
			mob.dropPreservedEquipment(serverLevel);
		}
		original.call(mob);
	}

	@Unique
	private CmsConfig.DespawnMode cms$despawnMode() {
		RuntimeConfig.CompiledMob mob = ConfigManager.active().mob(getType());
		return mob == null ? CmsConfig.DespawnMode.DEFAULT : mob.despawn;
	}

	/** Forced despawn never applies to tamed mobs, and applies to named mobs only when enabled. */
	@Unique
	private boolean cms$forced() {
		if (cms$despawnMode() != CmsConfig.DespawnMode.FORCE) {
			return false;
		}
		if ((Object) this instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return false;
		}
		return !hasCustomName() || ConfigManager.active().raw.despawn.forceIncludesNamed;
	}

	@Unique
	private boolean cms$pickupDespawnAllowed() {
		return cms$persistentFromPickup && !hasCustomName() && ConfigManager.active().raw.despawn.itemPickersDespawn;
	}
}
