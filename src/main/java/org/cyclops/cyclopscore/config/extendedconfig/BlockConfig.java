package org.cyclops.cyclopscore.config.extendedconfig;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.model.IDynamicModelElement;
import org.cyclops.cyclopscore.config.ConfigurableType;
import org.cyclops.cyclopscore.config.configurabletypeaction.BlockAction;
import org.cyclops.cyclopscore.helper.MinecraftHelpers;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.item.ItemInformationProvider;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Config for blocks.
 * @author rubensworks
 * @see ExtendedConfig
 */
public abstract class BlockConfig extends ExtendedConfigForge<BlockConfig, Block> implements IModelProviderConfig {

    @Nullable
    private final BiFunction<BlockConfig, Block, ? extends Item> itemConstructor;

    @Nullable
    private Item itemInstance;

    @OnlyIn(Dist.CLIENT)
    public ModelResourceLocation dynamicBlockVariantLocation;
    @OnlyIn(Dist.CLIENT)
    public ModelResourceLocation dynamicItemVariantLocation;

    public BlockConfig(ModBase mod, String namedId, Function<BlockConfig, ? extends Block> blockConstructor,
                       @Nullable BiFunction<BlockConfig, Block, ? extends Item> itemConstructor) {
        super(mod, namedId, blockConstructor);
        this.itemConstructor = itemConstructor;
    }

    protected static BiFunction<BlockConfig, Block, ? extends BlockItem> getDefaultItemConstructor(ModBase mod) {
        return getDefaultItemConstructor(mod, null);
    }

    protected static BiFunction<BlockConfig, Block, ? extends BlockItem> getDefaultItemConstructor(ModBase mod, @Nullable Function<Item.Properties, Item.Properties> itemPropertiesModifier) {
        return (eConfig, block) -> {
            Item.Properties itemProperties = new Item.Properties();
            if (itemPropertiesModifier != null) {
                itemProperties = itemPropertiesModifier.apply(itemProperties);
            }
            return new BlockItem(block, itemProperties);
        };
    }

    @Nullable
    public BiFunction<BlockConfig, Block, ? extends Item> getItemConstructor() {
        return itemConstructor;
    }

    @Nullable
    public Item getItemInstance() {
        return itemInstance;
    }

    public void setItemInstance(@Nullable Item itemInstance) {
        this.itemInstance = itemInstance;
    }

    @Override
    public String getModelName(ItemStack itemStack) {
        return getNamedId();
    }

    @Override
    public String getTranslationKey() {
        return "block." + getMod().getModId() + "." + getNamedId();
    }

    @Override
    public String getFullTranslationKey() {
        return getTranslationKey();
    }

    @Override
    public ConfigurableType getConfigurableType() {
        return ConfigurableType.BLOCK;
    }

    /**
     * Register default block and item models for this block.
     * This should only be used when registering dynamic models.
     * @return The pair of block resource location and item resource location.
     */
    @OnlyIn(Dist.CLIENT)
    public Pair<ModelResourceLocation, ModelResourceLocation> registerDynamicModel() {
        ResourceLocation blockName = ResourceLocation.fromNamespaceAndPath(getMod().getModId(), getNamedId());
        ModelResourceLocation blockLocation = new ModelResourceLocation(blockName, "");
        ModelResourceLocation itemLocation = new ModelResourceLocation(blockName, "inventory");
        return Pair.of(blockLocation, itemLocation);
    }

    @Override
    public void onRegistered() {
        super.onRegistered();
        for (ItemStack itemStack : defaultCreativeTabEntries()) {
            getMod().registerDefaultCreativeTabEntry(itemStack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    protected Collection<ItemStack> defaultCreativeTabEntries() {
        return Collections.singleton(new ItemStack(getInstance()));
    }

    @Override
    public void onForgeRegistered() {
        super.onForgeRegistered();

        // Add item to information provider
        Item item = getItemInstance();
        if (item != null) {
            ItemInformationProvider.registerItem(item);
        }

        // Handle dynamic models
        if(MinecraftHelpers.isClientSide() && getInstance() instanceof IDynamicModelElement &&
                ((IDynamicModelElement) getInstance()).hasDynamicModel()) {
            BlockAction.handleDynamicBlockModel(this);
        }
    }

    @Override
    public Registry<? super Block> getRegistry() {
        return BuiltInRegistries.BLOCK;
    }

    /**
     * @return An optional color handler for the block instance.
     */
    @OnlyIn(Dist.CLIENT)
    @Nullable
    public BlockColor getBlockColorHandler() {
        return null;
    }

}
