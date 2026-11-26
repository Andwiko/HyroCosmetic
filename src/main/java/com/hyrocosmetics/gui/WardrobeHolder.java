package com.hyrocosmetics.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class WardrobeHolder implements InventoryHolder {

    private Inventory inventory;
    private final WardrobeGui gui;
    private final boolean isCategoryView;

    public WardrobeHolder(WardrobeGui gui, boolean isCategoryView) {
        this.gui = gui;
        this.isCategoryView = isCategoryView;
    }

    public WardrobeGui getGui() {
        return gui;
    }

    public boolean isCategoryView() {
        return isCategoryView;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
