package org.cyclops.cyclopscore.persist.nbt;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.CyclopsCore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Objects that are serializable to NBT.
 * @author rubensworks
 *
 */
public interface INBTSerializable {

	/**
	 * Convert the data to an NBT tag.
	 * @return The NBT tag.
	 */
	public NBTTagCompound toNBT();
	/**
	 * Read the data from an NBT tag and place it in this object.
	 * @param tag The tag to read from.
	 */
	public void fromNBT(NBTTagCompound tag);

    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class SelfNBTClassType extends NBTClassType<INBTSerializable> {

        private final Class fieldType;

        @Override
        protected void writePersistedField(String name, INBTSerializable object, NBTTagCompound tag) {
            try {
                Method method = fieldType.getMethod("toNBT");
                tag.setTag(name, (NBTBase) method.invoke(object));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No method toNBT for field " + name + " of class " + fieldType + " was found.");
            } catch (InvocationTargetException e) {
                e.getTargetException().printStackTrace();
                throw new RuntimeException("Error in toNBT for field " + name + ". Error: " + e.getTargetException().getMessage());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could invoke toNBT for " + name + ".");
            }

        }

        @Override
        protected INBTSerializable readPersistedField(String name, NBTTagCompound tag) {
            try {
                Constructor constructor = fieldType.getConstructor();
                if(constructor == null) {
                    throw new RuntimeException("The NBT serializable " + name + " of class " + fieldType + " must " +
                            "have a constructor without parameters.");
                }
                Method method = fieldType.getMethod("fromNBT", NBTTagCompound.class);
                INBTSerializable obj = (INBTSerializable) constructor.newInstance();
                if(tag.hasKey(name)) {
                    method.invoke(obj, tag.getTag(name));
                } else {
                    CyclopsCore.clog(Level.WARN, String.format("The tag %s did not contain the key %s, skipping " +
                            "reading.", tag, name));
                }
                return obj;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No method fromNBT for field " + name + " of class " + fieldType + " was found.");
            } catch (InvocationTargetException e) {
                e.getTargetException().printStackTrace();
                throw new RuntimeException("Error in fromNBT for field " + name + ". Error: " + e.getTargetException().getMessage());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could invoke fromNBT for " + name + ".");
            } catch (InstantiationException e) {
                e.printStackTrace();
                throw new RuntimeException("Something went wrong while calling the empty constructor for " + name
                        + "of class " + fieldType + ".");
            }
        }
    }
	
}