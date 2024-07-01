package org.cyclops.cyclopscore.ingredient.recipe;

import com.google.common.collect.Lists;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import org.cyclops.commoncapabilities.api.capability.itemhandler.ItemMatch;
import org.cyclops.commoncapabilities.api.capability.recipehandler.IPrototypedIngredientAlternatives;
import org.cyclops.commoncapabilities.api.capability.recipehandler.PrototypedIngredientAlternativesList;
import org.cyclops.commoncapabilities.api.ingredient.IPrototypedIngredient;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.commoncapabilities.api.ingredient.PrototypedIngredient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for the conversion between {@link org.cyclops.commoncapabilities.api.capability.recipehandler.IRecipeDefinition}
 * and {@link Recipe}.
 */
public class IngredientRecipeHelpers {

    /**
     * Create prototyped ingredient alternatives from the given ingredient.
     * @param ingredient An ingredient.
     * @return Prototyped alternatives.
     */
    public static IPrototypedIngredientAlternatives<ItemStack, Integer> getPrototypesFromIngredient(ICustomIngredient ingredient) {
        List<IPrototypedIngredient<ItemStack, Integer>> items;
        if (ingredient instanceof CompoundIngredient) {
            items = Lists.newArrayList(new PrototypedIngredient<>(IngredientComponent.ITEMSTACK,
                    ingredient.getItems().findFirst().get(), ItemMatch.ITEM | ItemMatch.DATA));

        } else {
            items = ingredient.getItems()
                    .map(itemStack -> new PrototypedIngredient<>(IngredientComponent.ITEMSTACK, itemStack, ItemMatch.ITEM))
                    .collect(Collectors.toList());
        }
        return new PrototypedIngredientAlternativesList<>(items);
    }

}
