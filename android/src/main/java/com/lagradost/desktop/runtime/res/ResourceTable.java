package com.lagradost.desktop.runtime.res;

import android.content.res.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Backend of android.content.res.Resources. Implementations exist for the application's own
 * resources (generated from the upstream res/ folder) and for extension resources (resources.arsc).
 */
public interface ResourceTable {
    /** @return the best matching value for [id] in [config], or null when the id is unknown */
    ResValue get(int id, Configuration config);

    /** @return resource id or 0 */
    int getIdentifier(String type, String name);

    /** @return "package:type/entry" or null */
    String getResourceName(int id);

    String getPackageName();

    /** Open a file based resource (layout/drawable/xml/raw/font), path as stored in the table */
    InputStream openFile(String path) throws IOException;

    /** true if the file resource is compiled (binary AXML), false if plain text XML */
    boolean isBinaryXml(String path);

    /** Style attributes (attrId -> value) for a style resource, used by themes. May be empty. */
    default Map<Integer, ResValue> getStyle(int styleId, Configuration config) {
        return java.util.Collections.emptyMap();
    }

    /** Open an asset (assets/ folder) */
    default InputStream openAsset(String name) throws IOException {
        throw new java.io.FileNotFoundException(name);
    }

    default String[] listAssets(String path) {
        return new String[0];
    }
}
