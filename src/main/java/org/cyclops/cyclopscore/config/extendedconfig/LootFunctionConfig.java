package org.cyclops.cyclopscore.config.extendedconfig;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import org.cyclops.cyclopscore.config.ConfigurableType;
import org.cyclops.cyclopscore.init.ModBase;

/**
 * Config for loot functions.
 * @author rubensworks
 * @see ExtendedConfig
 */
public abstract class LootFunctionConfig extends ExtendedConfigForge<LootFunctionConfig, LootItemFunctionType<?>> {

    public LootFunctionConfig(ModBase mod, String namedId, LootItemFunctionType<?> lootItemFunctionType) {
        super(mod, namedId, (eConfig) -> lootItemFunctionType);
    }

    @Override
    public String getTranslationKey() {
        return "lootfunction." + getMod().getModId() + "." + getNamedId();
    }

    // Needed for config gui
    @Override
    public String getFullTranslationKey() {
        return getTranslationKey();
    }

    @Override
    public ConfigurableType getConfigurableType() {
        return ConfigurableType.LOOT_FUNCTION;
    }

    @Override
    public Registry<LootItemFunctionType<?>> getRegistry() {
        return BuiltInRegistries.LOOT_FUNCTION_TYPE;
    }
}
