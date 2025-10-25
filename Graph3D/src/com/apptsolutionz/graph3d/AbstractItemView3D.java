package com.apptsolutionz.graph3d;

import java.util.List;
import java.awt.Color;

/**
* Abstract class acting as an Adapter/View for converting a custom data object (T)
* into a renderable 3D Point structure. This is the extensibility hook.
*
* The RendererPanel will call these methods to get the necessary rendering information
* without knowing anything about the underlying type T.
*
* @param <T> The type of the custom data object in the list.
*/
public abstract class AbstractItemView3D<T> {

    /**
     * Retrieves the Point3D representation of an item at a specific index in the list.
     * This is the core method for mapping T's coordinate fields to the Point3D structure.
     *
     * @param list The full list of custom data objects.
     * @param index The index of the object to retrieve.
     * @return A Point3D object containing the X, Y, Z coordinates (as doubles) required for rendering.
     */
    public abstract Point3D getItem(List<T> list, int index);

    /**
     * Retrieves the color associated with the item at a specific index.
     *
     * @param list The full list of custom data objects.
     * @param index The index of the object to retrieve.
     * @return The Color for drawing the point.
     */
    public abstract Color getColor(List<T> list, int index);

    /**
     * Retrieves an optional label/name for the item at a specific index.
     *
     * @param list The full list of custom data objects.
     * @param index The index of the object to retrieve.
     * @return A String label, or null/empty if no label is desired.
     */
    public abstract String getLabel(List<T> list, int index);
    
    public abstract Point3D getMin(List<T> list);
    
    public abstract Point3D getMax(List<T> list);
}
