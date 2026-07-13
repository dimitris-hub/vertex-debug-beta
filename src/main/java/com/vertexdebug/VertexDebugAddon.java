package com.vertexdebug;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.vertexdebug.modules.ChunkFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entrypoint for the Vertex Debug addon.
 * Registered via the "meteor" entrypoint in fabric.mod.json, following
 * standard Meteor Client addon conventions (see meteor-addon-template).
 */
public class VertexDebugAddon extends MeteorAddon {
    public static final Logger LOG = LogManager.getLogger("Vertex Debug");

    // A dedicated category so the module shows up under its own heading in the module list / clickgui.
    public static final Category VERTEX_DEBUG = new Category("Vertex Debug");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Vertex Debug");

        Modules.get().add(new ChunkFinder());
    }

    @Override
    public void onRegisterCategories() {
        meteordevelopment.meteorclient.systems.modules.Modules.registerCategory(VERTEX_DEBUG);
    }

    @Override
    public String getPackage() {
        return "com.vertexdebug";
    }
}
