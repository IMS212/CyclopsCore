package org.cyclops.cyclopscore.init;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Data;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.client.key.IKeyRegistry;
import org.cyclops.cyclopscore.client.key.KeyRegistry;
import org.cyclops.cyclopscore.command.CommandConfig;
import org.cyclops.cyclopscore.command.CommandVersion;
import org.cyclops.cyclopscore.config.ConfigHandler;
import org.cyclops.cyclopscore.config.extendedconfig.CreativeModeTabConfig;
import org.cyclops.cyclopscore.helper.LoggerHelper;
import org.cyclops.cyclopscore.helper.MinecraftHelpers;
import org.cyclops.cyclopscore.modcompat.IMCHandler;
import org.cyclops.cyclopscore.modcompat.ModCompatLoader;
import org.cyclops.cyclopscore.modcompat.capabilities.CapabilityConstructorRegistry;
import org.cyclops.cyclopscore.network.PacketHandler;
import org.cyclops.cyclopscore.persist.world.WorldStorage;
import org.cyclops.cyclopscore.proxy.IClientProxy;
import org.cyclops.cyclopscore.proxy.ICommonProxy;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for mods which adds a few convenience methods.
 * Dont forget to call the supers for the init events.
 * @author rubensworks
 */
public abstract class ModBase<T extends ModBase> {

    private static final Map<String, ModBase<?>> MOD_BASES = Maps.newHashMap();

    public static final EnumReferenceKey<String> REFKEY_TEXTURE_PATH_GUI = EnumReferenceKey.create("texture_path_gui", String.class);
    public static final EnumReferenceKey<String> REFKEY_TEXTURE_PATH_MODELS = EnumReferenceKey.create("texture_path_models", String.class);
    public static final EnumReferenceKey<String> REFKEY_TEXTURE_PATH_SKINS = EnumReferenceKey.create("texture_path_skins", String.class);
    public static final EnumReferenceKey<Boolean> REFKEY_RETROGEN = EnumReferenceKey.create("retrogen", Boolean.class);
    public static final EnumReferenceKey<Boolean> REFKEY_CRASH_ON_INVALID_RECIPE = EnumReferenceKey.create("crash_on_invalid_recipe", Boolean.class);
    public static final EnumReferenceKey<Boolean> REFKEY_CRASH_ON_MODCOMPAT_CRASH = EnumReferenceKey.create("crash_on_modcompat_crash", Boolean.class);
    public static final EnumReferenceKey<Boolean> REFKEY_INFOBOOK_REWARDS = EnumReferenceKey.create("rewards", Boolean.class);

    private final String modId;
    private final LoggerHelper loggerHelper;
    private final ConfigHandler configHandler;
    private final Map<EnumReferenceKey<?>, Object> genericReference = Maps.newHashMap();
    private final List<WorldStorage> worldStorages = Lists.newLinkedList();
    private final RegistryManager registryManager;
    private final IKeyRegistry keyRegistry;
    private final PacketHandler packetHandler;
    private final ModCompatLoader modCompatLoader;
    private final CapabilityConstructorRegistry capabilityConstructorRegistry;
    private final IMCHandler imcHandler;
    private final IEventBus modEventBus;

    private ICommonProxy proxy;
    private ModContainer container;

    @Nullable
    private CreativeModeTab defaultCreativeTab = null;
    private final List<Pair<ItemStack, CreativeModeTab.TabVisibility>> defaultCreativeTabEntries = Lists.newArrayList();

    public ModBase(String modId, Consumer<T> instanceSetter, IEventBus modEventBus) {
        MOD_BASES.put(modId, this);
        instanceSetter.accept((T) this);
        this.modId = modId;
        this.modEventBus = modEventBus;
        this.loggerHelper = constructLoggerHelper();
        this.configHandler = constructConfigHandler();
        this.registryManager = constructRegistryManager();
        this.keyRegistry = new KeyRegistry();
        this.packetHandler = constructPacketHandler();
        this.modCompatLoader = constructModCompatLoader();
        this.capabilityConstructorRegistry = constructCapabilityConstructorRegistry();
        this.imcHandler = constructIMCHandler();

        // Register listeners
        getModEventBus().addListener(this::setup);
        getModEventBus().addListener(EventPriority.LOWEST, this::afterRegistriesCreated);
        getModEventBus().addListener(EventPriority.HIGHEST, this::beforeRegistriedFilled);
        if (MinecraftHelpers.isClientSide()) {
            getModEventBus().addListener(this::setupClient);
            getModEventBus().addListener(this::onRegisterKeyMappings);
        }
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Register proxies
        this.proxy = MinecraftHelpers.isClientSide() ? this.constructClientProxy() : this.constructCommonProxy();

        populateDefaultGenericReferences();

        // Initialize config handler
        this.onConfigsRegister(getConfigHandler());
        getConfigHandler().initialize(Lists.newArrayList());
        getConfigHandler().loadModInit();

        // Register default creative tab
        if (this.hasDefaultCreativeModeTab()) {
            this.getConfigHandler().addConfigurable(this.constructDefaultCreativeModeTabConfig());
        }

        loadModCompats(getModCompatLoader());
    }

    public String getModId() {
        return modId;
    }

    public LoggerHelper getLoggerHelper() {
        return loggerHelper;
    }

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public Map<EnumReferenceKey<?>, Object> getGenericReference() {
        return genericReference;
    }

    public List<WorldStorage> getWorldStorages() {
        return worldStorages;
    }

    public RegistryManager getRegistryManager() {
        return registryManager;
    }

    public IKeyRegistry getKeyRegistry() {
        return keyRegistry;
    }

    public PacketHandler getPacketHandler() {
        return packetHandler;
    }

    public ModCompatLoader getModCompatLoader() {
        return modCompatLoader;
    }

    public CapabilityConstructorRegistry getCapabilityConstructorRegistry() {
        return capabilityConstructorRegistry;
    }

    public IMCHandler getImcHandler() {
        return imcHandler;
    }

    @Nullable
    public CreativeModeTab getDefaultCreativeTab() {
        return defaultCreativeTab;
    }

    public List<Pair<ItemStack, CreativeModeTab.TabVisibility>> getDefaultCreativeTabEntries() {
        return defaultCreativeTabEntries;
    }

    public String getModName() {
        return getContainer().getModInfo().getDisplayName();
    }

    public IEventBus getModEventBus() {
        return modEventBus;
    }

    /**
     * @return The mod container of this mod.
     */
    public ModContainer getContainer() {
        if (container == null) {
            container = ModList.get().getModContainerById(this.getModId()).get();
        }
        return container;
    }

    protected LoggerHelper constructLoggerHelper() {
        return new LoggerHelper(getModId());
    }

    protected ConfigHandler constructConfigHandler() {
        return new ConfigHandler(this);
    }

    protected RegistryManager constructRegistryManager() {
        return new RegistryManager();
    }

    protected PacketHandler constructPacketHandler() {
        return new PacketHandler(this);
    }

    protected ModCompatLoader constructModCompatLoader() {
        return new ModCompatLoader(this);
    }

    protected CapabilityConstructorRegistry constructCapabilityConstructorRegistry() {
        return new CapabilityConstructorRegistry(this);
    }

    protected IMCHandler constructIMCHandler() {
        return new IMCHandler(this);
    }

    protected LiteralArgumentBuilder<CommandSourceStack> constructBaseCommand(Commands.CommandSelection selection, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(this.getModId());

        root.then(CommandConfig.make(this));
        root.then(CommandVersion.make(this));

        return root;
    }

    /**
     * Save a mod value.
     * @param key The key.
     * @param value The value.
     * @param <T> The value type.
     */
    public <T> void putGenericReference(EnumReferenceKey<T> key, T value) {
        genericReference.put(key, value);
    }

    private void populateDefaultGenericReferences() {
        putGenericReference(REFKEY_TEXTURE_PATH_GUI, "textures/gui/");
        putGenericReference(REFKEY_TEXTURE_PATH_MODELS, "textures/models/");
        putGenericReference(REFKEY_TEXTURE_PATH_SKINS, "textures/skins/");
        putGenericReference(REFKEY_RETROGEN, false);
        putGenericReference(REFKEY_CRASH_ON_INVALID_RECIPE, false);
        putGenericReference(REFKEY_CRASH_ON_MODCOMPAT_CRASH, false);
        putGenericReference(REFKEY_INFOBOOK_REWARDS, true);
    }

    /**
     * This is called only once to let the mod compatibilities register themselves.
     * @param modCompatLoader The loader.
     */
    protected void loadModCompats(ModCompatLoader modCompatLoader) {

    }

    /**
     * Get the value for a generic reference key.
     * The default keys can be found in {@link org.cyclops.cyclopscore.init.ModBase}.
     * @param key The key of a value.
     * @param <T> The type of value.
     * @return The value for the given key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getReferenceValue(EnumReferenceKey<T> key) {
        if(!genericReference.containsKey(key)) throw new IllegalArgumentException("Could not find " + key + " as generic reference item.");
        return (T) genericReference.get(key);
    }

    /**
     * Log a new info message for this mod.
     * @param message The message to show.
     */
    public void log(String message) {
        log(Level.INFO, message);
    }

    /**
     * Log a new message of the given level for this mod.
     * @param level The level in which the message must be shown.
     * @param message The message to show.
     */
    public void log(Level level, String message) {
        loggerHelper.log(level, message);
    }

    /**
     * Called on the Forge setup lifecycle event.
     * @param event The setup event.
     */
    protected void setup(FMLCommonSetupEvent event) {
        log(Level.TRACE, "setup()");

        // Iterate over all configs again
        getConfigHandler().loadSetup();

        // Register proxy things
        ICommonProxy proxy = getProxy();
        if(proxy != null) {
            proxy.registerEventHooks();
            proxy.registerPacketHandlers(getPacketHandler());
            proxy.registerTickHandlers();
        }
    }

    /**
     * Called on the Forge client setup lifecycle event.
     * @param event The setup event.
     */
    protected void setupClient(FMLClientSetupEvent event) {
        // Register proxy things
        ICommonProxy proxy = getProxy();
        if(proxy != null) {
            proxy.registerRenderers();
        }
    }

    protected void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ICommonProxy proxy = getProxy();
        if(proxy != null) {
            proxy.registerKeyBindings(getKeyRegistry(), event);
        }
    }

    /**
     * Load things after Forge registries have been created.
     * @param event The Forge registry creation event.
     */
    private void afterRegistriesCreated(NewRegistryEvent event) {
        getConfigHandler().loadForgeRegistries();
    }

    /**
     * Load things before Forge registries are being filled.
     * @param event The Forge registry filling event.
     */
    private void beforeRegistriedFilled(RegisterEvent event) {
        if (event.getRegistryKey().equals(BuiltInRegistries.ATTRIBUTE.key())) {
            // We only need to call this once, and the ATTRIBUTE event is emitted first.
            getConfigHandler().loadForgeRegistriesFilled();
        }
    }

    /**
     * Register the things that are related to when the server is starting.
     * @param event The Forge server starting event.
     */
    protected void onServerStarting(ServerStartingEvent event) {

    }

    protected void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(constructBaseCommand(event.getCommandSelection(), event.getBuildContext()));
    }

    /**
     * Register the things that are related to when the server is about to start.
     * @param event The Forge server about to start event.
     */
    protected void onServerAboutToStart(ServerAboutToStartEvent event) {
        for(WorldStorage worldStorage : worldStorages) {
            worldStorage.onAboutToStartEvent(event);
        }
    }

    /**
     * Register the things that are related to server starting.
     * @param event The Forge server started event.
     */
    protected void onServerStarted(ServerStartedEvent event) {
        for(WorldStorage worldStorage : worldStorages) {
            worldStorage.onStartedEvent(event);
        }
    }

    /**
     * Register the things that are related to server stopping, like persistent storage.
     * @param event The Forge server stopping event.
     */
    protected void onServerStopping(ServerStoppingEvent event) {
        for(WorldStorage worldStorage : worldStorages) {
            worldStorage.onStoppingEvent(event);
        }
    }

    /**
     * Register a new world storage type.
     * Make sure to call this at least before the event
     * {@link net.neoforged.neoforge.event.server.ServerStartedEvent} is called.
     * @param worldStorage The world storage to register.
     */
    public void registerWorldStorage(WorldStorage worldStorage) {
        worldStorages.add(worldStorage);
    }

    @OnlyIn(Dist.CLIENT)
    protected abstract IClientProxy constructClientProxy();

    @OnlyIn(Dist.DEDICATED_SERVER)
    protected abstract ICommonProxy constructCommonProxy();

    public void registerDefaultCreativeTabEntry(ItemStack itemStack, CreativeModeTab.TabVisibility visibility) {
        if (defaultCreativeTabEntries == null) {
            throw new IllegalStateException("Tried to register default tab entries after the CreativeModeTabEvent.BuildContents event");
        }
        if (itemStack.getCount() != 1) {
            throw new IllegalStateException("Tried to register default tab entries with a non-1-count ItemStack");
        }
        defaultCreativeTabEntries.add(Pair.of(itemStack, visibility));
    }

    protected CreativeModeTabConfig constructDefaultCreativeModeTabConfig() {
        return new CreativeModeTabConfig(this, "default", (config) -> this.defaultCreativeTab = this.constructDefaultCreativeModeTab(CreativeModeTab.builder()).build());
    }

    protected CreativeModeTab.Builder constructDefaultCreativeModeTab(CreativeModeTab.Builder builder) {
        return builder
                .title(Component.translatable("itemGroup." + getModId()))
                .icon(() -> new ItemStack(Items.BARRIER))
                .displayItems((parameters, output) -> {
                    for (Pair<ItemStack, CreativeModeTab.TabVisibility> entry : defaultCreativeTabEntries) {
                        output.accept(entry.getLeft(), entry.getRight());
                    }
                });
    }

    /**
     * @return If a default creative tab should be constructed.
     *         If so, make sure to override {@link #constructDefaultCreativeModeTab(CreativeModeTab.Builder)}.
     */
    protected boolean hasDefaultCreativeModeTab() {
        return true;
    };

    /**
     * Called when the configs should be registered.
     * @param configHandler The config handler to register to.
     */
    protected void onConfigsRegister(ConfigHandler configHandler) {

    }

    /**
     * @return The proxy for this mod.
     */
    public ICommonProxy getProxy() {
        return this.proxy;
    }

    @Override
    public String toString() {
        return getModId();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return object == this;
    }

    /**
     * Get the mod by id.
     * @param modId The mod id.
     * @return The mod instance or null.
     */
    @Nullable
    public static ModBase get(String modId) {
        return MOD_BASES.get(modId);
    }

    /**
     * Unique references to values that can be registered inside a mod.
     * @param <T> The type of value.
     */
    @Data
    public static class EnumReferenceKey<T> {

        private final String key;
        private final Class<T> type;

        private EnumReferenceKey(String key, Class<T> type) {
            this.key = key;
            this.type = type;
        }

        public static <T> EnumReferenceKey<T> create(String key, Class<T> type) {
            return new EnumReferenceKey<>(key, type);
        }

    }

}
