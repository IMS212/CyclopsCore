package org.cyclops.cyclopscore.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.datastructure.SingleCache;
import org.cyclops.cyclopscore.inventory.ItemLocation;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Packet with automatic coding and decoding of basic fields annotated with {@link CodecField}.
 * @author rubensworks
 *
 */
public abstract class PacketCodec<T extends PacketCodec<T>> extends PacketBase<T> {

    public static final int READ_STRING_MAX_LENGTH = 32767;

    private static Map<Class<?>, ICodecAction> codecActions = Maps.newHashMap();
    static {
        codecActions.put(String.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeUtf((String) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readUtf(32767); // The arg-less version is client-side only, so we copy its implementation.
            }
        });

        codecActions.put(double.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeDouble((Double) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readDouble();
            }
        });

        codecActions.put(int.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeInt((int) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readInt();
            }
        });

        codecActions.put(long.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeLong((long) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readLong();
            }
        });

        codecActions.put(short.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeShort((Short) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readShort();
            }
        });

        codecActions.put(boolean.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeBoolean((Boolean) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readBoolean();
            }
        });

        codecActions.put(float.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeFloat((Float) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readFloat();
            }
        });

        codecActions.put(Vector3d.class, new ICodecAction() {
            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                Vector3d v = (Vector3d)object;
                output.writeDouble(v.x);
                output.writeDouble(v.y);
                output.writeDouble(v.z);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                double x = input.readDouble();
                double y = input.readDouble();
                double z = input.readDouble();
                return new Vector3d(x, y, z);
            }
        });

        codecActions.put(Vec3i.class, new ICodecAction() {
            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                Vec3i v = (Vec3i)object;
                output.writeInt(v.getX());
                output.writeInt(v.getY());
                output.writeInt(v.getZ());
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                return new Vec3i(x, y, z);
            }
        });

        codecActions.put(Vec3.class, new ICodecAction() {
            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                Vec3 v = (Vec3)object;
                output.writeDouble(v.x);
                output.writeDouble(v.y);
                output.writeDouble(v.z);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                double x = input.readDouble();
                double y = input.readDouble();
                double z = input.readDouble();
                return new Vec3(x, y, z);
            }
        });

        codecActions.put(Map.class, new ICodecAction() {

            // Packet structure:
            // Map length (int)
            // --- end if length == 0
            // Key class name (UTF)
            // Value class name (UTF)
            // for length
            //   key
            //   value

            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                Map map = (Map) object;
                output.writeInt(map.size());
                Set<Map.Entry> entries = map.entrySet();
                ICodecAction keyAction = null;
                ICodecAction valueAction = null;
                for(Map.Entry entry : entries) {
                    if(keyAction == null) {
                        keyAction = getAction(entry.getKey().getClass());
                        output.writeUtf(entry.getKey().getClass().getName());
                    }
                    if(valueAction == null) {
                        valueAction = getAction(entry.getValue().getClass());
                        output.writeUtf(entry.getValue().getClass().getName());
                    }
                    keyAction.encode(entry.getKey(), output);
                    valueAction.encode(entry.getValue(), output);
                }
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                Map map = Maps.newHashMap();
                int size = input.readInt();
                if(size == 0) {
                    return map;
                }
                try {
                    ICodecAction keyAction = getAction(Class.forName(input.readUtf(READ_STRING_MAX_LENGTH)));
                    ICodecAction valueAction = getAction(Class.forName(input.readUtf(READ_STRING_MAX_LENGTH)));
                    for(int i = 0; i < size; i++) {
                        Object key = keyAction.decode(input);
                        Object value = valueAction.decode(input);
                        map.put(key, value);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return map;
            }
        });

        codecActions.put(CompoundTag.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeNbt((CompoundTag) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readNbt();
            }
        });

        codecActions.put(Tag.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                CompoundTag tag = new CompoundTag();
                tag.put("v", (Tag) object);
                output.writeNbt(tag);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                CompoundTag tag = input.readNbt();
                return tag.get("v");
            }
        });

        codecActions.put(ItemStack.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(output, (ItemStack) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return ItemStack.OPTIONAL_STREAM_CODEC.decode(input);
            }
        });

        codecActions.put(FluidStack.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                FluidStack.OPTIONAL_STREAM_CODEC.encode(output, (FluidStack) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return FluidStack.OPTIONAL_STREAM_CODEC.decode(input);
            }
        });

        codecActions.put(Direction.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeInt(((Direction) object).ordinal());
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return Direction.values()[input.readInt()];
            }
        });

        codecActions.put(BlockPos.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeBlockPos((BlockPos) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return input.readBlockPos();
            }
        });

        codecActions.put(ResourceKey.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeUtf(((ResourceKey) object).registry().toString());
                output.writeUtf(((ResourceKey) object).location().toString());
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return ResourceKey.create(
                        ResourceKey.createRegistryKey(ResourceLocation.parse(input.readUtf(READ_STRING_MAX_LENGTH))),
                        ResourceLocation.parse(input.readUtf(READ_STRING_MAX_LENGTH)));
            }
        });

        codecActions.put(DimPos.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                write(output, ((DimPos) object).getLevelKey());
                write(output, ((DimPos) object).getBlockPos());
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                ResourceKey dimensionType = read(input, ResourceKey.class);
                BlockPos blockPos = read(input, BlockPos.class);
                return DimPos.of(dimensionType, blockPos);
            }
        });

        codecActions.put(List.class, new ICodecAction() {

            // Packet structure:
            // list length (int)
            // --- end if length == 0
            // Value class name (UTF)
            // 	id + value
            // -1

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                List<?> list = (List<?>) object;
                output.writeInt(list.size());
                if(list.size() == 0) return;
                ICodecAction valueAction = null;
                for(int i = 0; i < list.size(); i++) {
                    Object value = list.get(i);
                    if(value != null) {
                        if (valueAction == null) {
                            valueAction = getAction(value.getClass());
                            output.writeUtf(value.getClass().getName());
                        }
                        output.writeInt(i);
                        valueAction.encode(value, output);
                    }
                }
                if(valueAction == null) {
                    output.writeUtf("__noclass");
                } else {
                    output.writeInt(-1);
                }
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                List list;
                int size = input.readInt();
                if(size == 0) {
                    return Collections.emptyList();
                } else {
                    list = Lists.newArrayListWithExpectedSize(size);
                }
                try {
                    String className = input.readUtf(READ_STRING_MAX_LENGTH);
                    if(!className.equals("__noclass")) {
                        ICodecAction valueAction = getAction(Class.forName(className));
                        int i, currentLength = 0;
                        while((i = input.readInt()) >= 0) {
                            while(currentLength < i) {
                                list.add(null);
                                currentLength++;
                            }
                            Object value = valueAction.decode(input);
                            list.add(value);
                            currentLength++;
                        }
                        while(currentLength < size) {
                            list.add(null);
                            currentLength++;
                        }
                    } else {
                        for (int i = 0; i < size; i++) {
                            list.add(null);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return list;
            }
        });

        codecActions.put(Pair.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                output.writeUtf(((Pair) object).getLeft().getClass().getName());
                output.writeUtf(((Pair) object).getRight().getClass().getName());
                write(output, ((Pair) object).getLeft());
                write(output, ((Pair) object).getRight());
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                try {
                    ICodecAction keyAction = getAction(Class.forName(input.readUtf(READ_STRING_MAX_LENGTH)));
                    ICodecAction valueAction = getAction(Class.forName(input.readUtf(READ_STRING_MAX_LENGTH)));
                    Object key = keyAction.decode(input);
                    Object value = valueAction.decode(input);
                    return Pair.of(key, value);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return Pair.of(null, null);
            }
        });

        codecActions.put(ItemLocation.class, new ICodecAction() {

            @Override
            public void encode(Object object, RegistryFriendlyByteBuf output) {
                ItemLocation.writeToPacketBuffer(output, (ItemLocation) object);
            }

            @Override
            public Object decode(RegistryFriendlyByteBuf input) {
                return ItemLocation.readFromPacketBuffer(input);
            }
        });
    }

    public PacketCodec(Type<T> type) {
        super(type);
    }

    /**
     * Register a new coded action.
     * @param clazz A class type.
     * @param action A codec action for the given type.
     */
    public static void addCodedAction(Class<?> clazz, ICodecAction action) {
        codecActions.put(clazz, action);
    }

    protected SingleCache<Void, List<Field>> fieldCache = new SingleCache<Void, List<Field>>(
            new SingleCache.ICacheUpdater<Void, List<Field>>() {

        @Override
        public List<Field> getNewValue(Void key) {
            List<Field> fieldList = Lists.newLinkedList();

            Class<?> clazz = PacketCodec.this.getClass();
            for (; clazz != PacketCodec.class && clazz != null; clazz = clazz.getSuperclass()) {
                Field[] fields = clazz.getDeclaredFields();

                // Sort this because the Java API tells us that getDeclaredFields()
                // does not deterministically define the order of the fields in the array.
                // Otherwise we might get nasty class cast exceptions when running in SMP.
                Arrays.sort(fields, new Comparator<Field>() {

                    @Override
                    public int compare(Field o1, Field o2) {
                        return o1.getName().compareTo(o2.getName());
                    }

                });

                for (final Field field : fields) {
                    if (field.isAnnotationPresent(CodecField.class)) {
                        fieldList.add(field);
                    }
                }
            }

            return fieldList;
        }

        @Override
        public boolean isKeyEqual(Void cacheKey, Void newKey) {
            return true;
        }

    });

    @Nullable
    protected static ICodecAction getActionSuper(Class<?> clazz) {
        if(ClassUtils.isPrimitiveWrapper(clazz)) {
            clazz = ClassUtils.wrapperToPrimitive(clazz);
        }
        ICodecAction action = codecActions.get(clazz);
        if(action == null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                action = codecActions.get(iface);
                if (action != null) {
                    return action;
                }
            }
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return getActionSuper(superClass);
            } else {
                return null;
            }
        }
        return action;
    }

    public static ICodecAction getAction(Class<?> clazz) {
        ICodecAction action = getActionSuper(clazz);
        if(action == null) {
            System.err.println("No ICodecAction was found for " + clazz
                    + ". You should add one in PacketCodec.");
        }
        return action;
    }

    private void loopCodecFields(ICodecRunnable runnable) {
        for (Field field : fieldCache.get(null)) {
            Class<?> clazz = field.getType();
            ICodecAction action = getAction(clazz);

            // Make private fields temporarily accessible.
            boolean accessible = field.isAccessible();
            if (!accessible) {
                field.setAccessible(true);
            }
            runnable.run(field, action);
        }
    }

    @Override
    public void encode(final RegistryFriendlyByteBuf output) {
        loopCodecFields((field, action) -> {
            Object object = null;
            try {
                object = field.get(PacketCodec.this);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
            action.encode(object, output);
        });
    }

    @Override
    public void decode(final RegistryFriendlyByteBuf input) {
        loopCodecFields((field, action) -> {
            Object object = action.decode(input);
            try {
                field.set(PacketCodec.this, object);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Write the given object into the packet buffer.
     * @param packetBuffer A packet buffer.
     * @param object An object.
     */
    public static void write(RegistryFriendlyByteBuf packetBuffer, Object object) {
        ICodecAction action = Objects.requireNonNull(getActionSuper(object.getClass()),
                "No codec action was registered for " + object.getClass().getName());
        action.encode(object, packetBuffer);
    }

    /**
     * Read the an object of the given type from the packet buffer.
     * @param <T> The type of object.
     * @param packetBuffer A packet buffer.
     * @param clazz The class type to read.
     * @return The read object.
     */
    public static <T> T read(RegistryFriendlyByteBuf packetBuffer, Class<T> clazz) {
        ICodecAction action = Objects.requireNonNull(getActionSuper(clazz));
        return (T) action.decode(packetBuffer);
    }

    public static interface ICodecAction {

        /**
         * Encode the given object.
         * @param object The object to encode into the output.
         * @param output The byte array to encode to.
         */
        public void encode(Object object, RegistryFriendlyByteBuf output);

        /**
         * Decode from the input.
         * @param input The byte array to decode from.
         * @return The object to return after reading it from the input.
         */
        public Object decode(RegistryFriendlyByteBuf input);

    }

    public static interface ICodecRunnable {

        /**
         * Run a type of codec.
         * @param field The field annotated with {@link CodecField}.
         * @param action The action that must be applied to the field.
         */
        public void run(Field field, ICodecAction action);

    }

}
