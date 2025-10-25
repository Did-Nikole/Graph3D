package com.apptsolutionz.graph3d;

/**
* Data class representing a point in 3D space (x, y, z).
* This class is the required structure for the rendering engine, as the math
* necessitates double-precision floating point coordinates.
*/
public class Point3D {
    double x;
    double y;
    double z;

    /**
     * Constructs a 3D point.
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The z-coordinate.
     */
    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // --- Accessor Methods ---

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}