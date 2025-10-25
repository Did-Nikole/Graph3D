package com.apptsolutionz.graph3d;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;

/**
* A custom JPanel that renders 3D objects onto a 2D surface.
* It uses an AbstractItemView3D adapter to obtain the 3D coordinates
* from a generic list of custom objects. Now includes toggleable Perspective Projection,
* Z-depth sorting (Painter's Algorithm), and **automatic scaling to fit the data**.
*
* @param <T> The type of the custom data object in the underlying list.
*/
@SuppressWarnings("serial")
public class RendererPanel<T> extends JPanel {
    private List<T> dataList;
    private AbstractItemView3D<T> adapter;

    // --- MODIFIED: Separated base scale from user-controlled zoom ---
    private double baseScale = 1.0;  // Auto-calculated scale to fit the data model
    private double userZoom = 1.0;   // User-controlled zoom via mouse wheel, starts at 1x
    private double rotationX = Math.PI / 6;
    private double rotationY = Math.PI / 4;

    private int lastMouseX;
    private int lastMouseY;

    private boolean usePerspective = false;
    private final double viewerDistance = 30.0;
    private Point3D mins;
    private Point3D maxs;
    private Point3D mids = new Point3D(0, 0, 0);

    // Inner class ProjectedPoint (Unchanged)
    private class ProjectedPoint {
        final int screenX;
        final int screenY;
        final double zDepth;
        final int size;
        final Color color;
        final String label;

        ProjectedPoint(int screenX, int screenY, double zDepth, int size, Color color, String label) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.zDepth = zDepth;
            this.size = size;
            this.color = color;
            this.label = label;
        }
    }

    // Inner class ScreenResult (Unchanged)
    private class ScreenResult {
        final int x;
        final int y;
        final double z;
        final double factor;

        ScreenResult(int x, int y, double z, double factor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.factor = factor;
        }
    }

    /**
     * Initializes the panel.
     */
    public RendererPanel(List<T> dataList, AbstractItemView3D<T> adapter) {
        this.setDataSet(dataList, adapter);
        setBackground(Color.DARK_GRAY);
        setupMouseListeners();

        // NEW: Add a listener to rescale the view when the window is resized
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateBaseScale();
                repaint();
            }
        });
    }

    public void setDataSet(List<T> dataList, AbstractItemView3D<T> adapter) {
        this.dataList = dataList;
        this.adapter = adapter;
        this.dataSetChanged();
    }

    public void dataSetChanged() {
        System.out.print("[Debug] Dataset Changed ");
        if (dataList != null && !dataList.isEmpty()) {
            this.mins = adapter.getMin(dataList);
            this.maxs = adapter.getMax(dataList);
            System.out.println(dataList.size());
        } else {
            this.mins = new Point3D(0, 0, 0);
            this.maxs = new Point3D(0, 0, 0);
            System.out.println(0);
        }
        this.mids.x = (this.mins.x + this.maxs.x) / 2.0;
        this.mids.y = (this.mins.y + this.maxs.y) / 2.0;
        this.mids.z = (this.mins.z + this.maxs.z) / 2.0;
        
        // MODIFIED: Recalculate the base scale whenever the data changes
        updateBaseScale();
    }

    /**
     * NEW: Calculates the base scale factor to fit the current dataset
     * snugly within the panel's viewable area.
     */
    private void updateBaseScale() {
        if (getWidth() == 0 || getHeight() == 0 || dataList == null || dataList.isEmpty()) {
            baseScale = 1.0; // Default if no data or not visible yet
            return;
        }

        double rangeX = maxs.x - mins.x;
        double rangeY = maxs.y - mins.y;
        double rangeZ = maxs.z - mins.z;
        double maxRange = Math.max(rangeX, Math.max(rangeY, rangeZ));

        if (maxRange < 1e-9) { // If data is a single point or has no volume
            baseScale = 50.0; // Give it a reasonable default size
            return;
        }

        // Target size: 80% of the smaller panel dimension
        double targetScreenSize = Math.min(getWidth(), getHeight()) * 0.80;

        baseScale = targetScreenSize / maxRange;
    }


    public void togglePerspective() {
        this.usePerspective = !this.usePerspective;
        repaint();
    }

    public boolean isPerspectiveEnabled() {
        return usePerspective;
    }

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                double deltaX = e.getX() - lastMouseX;
                double deltaY = e.getY() - lastMouseY;
                
                rotationY += deltaX * 0.01;
                rotationX += deltaY * 0.01;
                
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                repaint();
            }
        });

        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double notches = e.getWheelRotation();
                double zoomChange = 1.0 - (notches * 0.1);

                // MODIFIED: Affect the userZoom, not the old combined zoom variable
                userZoom *= zoomChange;
                userZoom = Math.max(0.1, userZoom); // Prevent zooming in too far or reversing

                repaint();
            }
        });
    }

    /**
     * Helper method to perform rotation and projection for any 3D point.
     */
    private ScreenResult projectToScreen(double x, double y, double z) {
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        double cosX = Math.cos(rotationX);
        double sinX = Math.sin(rotationX);
        double cosY = Math.cos(rotationY);
        double sinY = Math.sin(rotationY);

        double yPrime = y * cosX - z * sinX;
        double zPrime = y * sinX + z * cosX;
        double xDoublePrime = x * cosY + zPrime * sinY;
        double yDoublePrime = yPrime;
        double zDoublePrime = zPrime * cosY - x * sinY;

        double factor = 1.0;
        if (usePerspective) {
            if (viewerDistance - zDoublePrime > 0.1) {
                factor = viewerDistance / (viewerDistance - zDoublePrime);
            } else {
                return null;
            }
        }

        // MODIFIED: Combine baseScale and userZoom for the final effective scale
        double effectiveZoom = baseScale * userZoom;
        int screenX = (int) (centerX + xDoublePrime * effectiveZoom * factor);
        int screenY = (int) (centerY - yDoublePrime * effectiveZoom * factor);
        
        return new ScreenResult(screenX, screenY, zDoublePrime, factor);
    }
    
    /**
     * NEW: Draws X, Y, and Z axes on the "far" corner of the data's bounding box.
     * This ensures they provide context without covering the data points.
     * @param g2d The graphics context to draw on.
     */
    private void drawAxes(Graphics2D g2d) {
        if (mins == null || maxs == null || Objects.equals(mins, maxs)) {
            return; // Don't draw axes if there's no data or data has no volume
        }

        // 1. Define the 8 corners of the bounding box
        Point3D[] corners = new Point3D[8];
        corners[0] = new Point3D(mins.x, mins.y, mins.z);
        corners[1] = new Point3D(maxs.x, mins.y, mins.z);
        corners[2] = new Point3D(mins.x, maxs.y, mins.z);
        corners[3] = new Point3D(maxs.x, maxs.y, mins.z);
        corners[4] = new Point3D(mins.x, mins.y, maxs.z);
        corners[5] = new Point3D(maxs.x, mins.y, maxs.z);
        corners[6] = new Point3D(mins.x, maxs.y, maxs.z);
        corners[7] = new Point3D(maxs.x, maxs.y, maxs.z);

        // 2. Find the corner furthest from the camera (smallest z-depth)
        Point3D farCorner = null;
        double minZ = Double.POSITIVE_INFINITY;

        for (Point3D corner : corners) {
            ScreenResult sr = projectToScreen(corner.x - mids.x, corner.y - mids.y, corner.z - mids.z);
            if (sr != null && sr.z < minZ) {
                minZ = sr.z;
                farCorner = corner;
            }
        }

        if (farCorner == null) {
            return; // All corners are behind the camera
        }

        // 3. Setup for drawing
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        DecimalFormat df = new DecimalFormat("#,##0.0"); // Formatter for min/max values

        // 4. Project the origin point and create its full label
        ScreenResult originScreen = projectToScreen(farCorner.x - mids.x, farCorner.y - mids.y, farCorner.z - mids.z);
        if (originScreen == null) return;

        // NEW: Create a single formatted string for the (x,y,z) origin
        String originLabel = String.format("(%s, %s, %s)",
            df.format(farCorner.x),
            df.format(farCorner.y),
            df.format(farCorner.z)
        );
        // NEW: Draw the full origin label once
        g2d.drawString(originLabel, originScreen.x - 20, originScreen.y + 15);


        // --- Draw X-Axis ---
        Point3D adjacentX = new Point3D(farCorner.x == mins.x ? maxs.x : mins.x, farCorner.y, farCorner.z);
        Point3D midpointX = new Point3D((farCorner.x + adjacentX.x) / 2, farCorner.y, farCorner.z);
        ScreenResult endXScreen = projectToScreen(adjacentX.x - mids.x, adjacentX.y - mids.y, adjacentX.z - mids.z);
        ScreenResult midXScreen = projectToScreen(midpointX.x - mids.x, midpointX.y - mids.y, midpointX.z - mids.z);

        if (endXScreen != null) {
            g2d.drawLine(originScreen.x, originScreen.y, endXScreen.x, endXScreen.y);
            // MODIFIED: The origin label is now drawn above, so we only draw the endpoint value.
            g2d.drawString(df.format(adjacentX.x), endXScreen.x - 8, endXScreen.y + 15);
            if (midXScreen != null) g2d.drawString("X", midXScreen.x - 4, midXScreen.y - 8);
        }

        // --- Draw Y-Axis ---
        Point3D adjacentY = new Point3D(farCorner.x, farCorner.y == mins.y ? maxs.y : mins.y, farCorner.z);
        Point3D midpointY = new Point3D(farCorner.x, (farCorner.y + adjacentY.y) / 2, farCorner.z);
        ScreenResult endYScreen = projectToScreen(adjacentY.x - mids.x, adjacentY.y - mids.y, adjacentY.z - mids.z);
        ScreenResult midYScreen = projectToScreen(midpointY.x - mids.x, midpointY.y - mids.y, midpointY.z - mids.z);

        if (endYScreen != null) {
            g2d.drawLine(originScreen.x, originScreen.y, endYScreen.x, endYScreen.y);
            g2d.drawString(df.format(adjacentY.y), endYScreen.x - 8, endYScreen.y + 15);
            if (midYScreen != null) g2d.drawString("Y", midYScreen.x + 8, midYScreen.y + 4);
        }

        // --- Draw Z-Axis ---
        Point3D adjacentZ = new Point3D(farCorner.x, farCorner.y, farCorner.z == mins.z ? maxs.z : mins.z);
        Point3D midpointZ = new Point3D(farCorner.x, farCorner.y, (farCorner.z + adjacentZ.z) / 2);
        ScreenResult endZScreen = projectToScreen(adjacentZ.x - mids.x, adjacentZ.y - mids.y, adjacentZ.z - mids.z);
        ScreenResult midZScreen = projectToScreen(midpointZ.x - mids.x, midpointZ.y - mids.y, midpointZ.z - mids.z);

        if (endZScreen != null) {
            g2d.drawLine(originScreen.x, originScreen.y, endZScreen.x, endZScreen.y);
            g2d.drawString(df.format(adjacentZ.z), endZScreen.x - 8, endZScreen.y + 15);
            if (midZScreen != null) g2d.drawString("Z", midZScreen.x - 4, midZScreen.y - 8);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ===================================
        // PHASE 0: DRAW AXES
        // ===================================
        // MODIFIED: Call the new method to draw axes first, so they appear behind the data.
        drawAxes(g2d);
        
        if (dataList == null || dataList.isEmpty()) {
            g2d.setColor(Color.WHITE);
            g2d.drawString("No data to display.", getWidth() / 2 - 50, getHeight() / 2);
            return;
        }

        List<ProjectedPoint> projectedPoints = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            Point3D p = adapter.getItem(dataList, i);
            
            double x = p.getX() - mids.x;
            double y = p.getY() - mids.y;
            double z = p.getZ() - mids.z;
            
            ScreenResult sr = projectToScreen(x, y, z);

            if (sr == null) continue;

            int dotSize = (int) (6 * sr.factor);
            dotSize = Math.max(2, Math.min(15, dotSize));
            Color pointColor = adapter.getColor(dataList, i);
            String label = adapter.getLabel(dataList, i);

            projectedPoints.add(new ProjectedPoint(
                sr.x, sr.y, sr.z, dotSize, pointColor, label
            ));
        }

        projectedPoints.sort(Comparator.comparingDouble(p -> p.zDepth));

        for (ProjectedPoint pp : projectedPoints) {
            g2d.setColor(pp.color);
            g2d.fillOval(pp.screenX - pp.size / 2, pp.screenY - pp.size / 2, pp.size, pp.size);
            if (pp.label != null && !pp.label.trim().isEmpty()) {
                g2d.drawString(pp.label, pp.screenX + pp.size, pp.screenY - pp.size);
            }
        }

        g2d.setColor(Color.WHITE);
        g2d.drawString(String.format("Rotation (Pitch/Yaw): %.1f°, %.1f°",
                Math.toDegrees(rotationX), Math.toDegrees(rotationY)), 10, 20);
        // MODIFIED: Display the user-friendly zoom factor
        g2d.drawString(String.format("Zoom: %.2fx", userZoom), 10, 40);
        g2d.drawString(String.format("Projection: %s (D=%.1f)",
                usePerspective ? "Perspective" : "Orthographic", viewerDistance), 10, 60);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("Controls: Drag mouse (LMB) to rotate, use mouse wheel to zoom.", 10, getHeight() - 10);
    }
}