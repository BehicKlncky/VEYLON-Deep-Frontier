package com.veylon.gfx.model;

import com.veylon.engine.Mesh;
import com.veylon.engine.ShaderProgram;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * One cuboid in a hierarchical model: pivot relative to the parent pivot,
 * a box (center + size) hung on that pivot, per-part color/emissive, and a
 * pose (rotation + translation) written by the Animator each frame.
 */
public class ModelPart {

    public final String name;
    // Pivot position relative to the parent's pivot (model units = meters).
    public float pivotX, pivotY, pivotZ;
    // Box center relative to this pivot, and box size.
    public float boxX, boxY, boxZ, sizeX, sizeY, sizeZ;
    public float r, g, b;
    public float emissive;
    public boolean visible = true;
    /**
     * Draw this part's own half of a {@link #split} even while the chain is
     * straight. Set when the tip is drawn somewhere else, as when a limb is cut
     * at the joint; without it a hidden straight tip would still be drawn as
     * part of this part's merged whole box. Cleared by {@link #resetPose}.
     */
    public boolean forceSplitDraw;

    // Pose, reset/overwritten by the animator.
    public float rotX, rotY, rotZ;
    public float poseX, poseY, poseZ;
    public float scale = 1f;

    public final List<ModelPart> children = new ArrayList<>();
    private final Matrix4f local = new Matrix4f();
    private final Matrix4f draw = new Matrix4f();
    private ModelPart straightChild;
    private float wholeX, wholeY, wholeZ, wholeSizeX, wholeSizeY, wholeSizeZ;

    /** Split a box at its midpoint. A straight chain draws the original box exactly. */
    public ModelPart split(String childName, boolean alongY) {
        wholeX = boxX; wholeY = boxY; wholeZ = boxZ;
        wholeSizeX = sizeX; wholeSizeY = sizeY; wholeSizeZ = sizeZ;
        ModelPart tip = new ModelPart(childName).color(r, g, b).emissive(emissive);
        if (alongY) {
            tip.pivot(0, boxY, 0).box(boxX, -sizeY * 0.25f, boxZ, sizeX, sizeY * 0.5f, sizeZ);
            boxY += sizeY * 0.25f;
            sizeY *= 0.5f;
        } else {
            tip.pivot(0, 0, boxZ).box(boxX, boxY, sizeZ * 0.25f, sizeX, sizeY, sizeZ * 0.5f);
            boxZ -= sizeZ * 0.25f;
            sizeZ *= 0.5f;
        }
        straightChild = tip;
        child(tip);
        return this;
    }

    private boolean straight() {
        return straightChild != null && straightChild.rotX == 0 && straightChild.rotY == 0
                && straightChild.rotZ == 0 && straightChild.poseX == 0
                && straightChild.poseY == 0 && straightChild.poseZ == 0 && straightChild.scale == 1;
    }

    /**
     * True when {@link #render} draws this part's original unsplit box and
     * skips the tip's half: the chain is straight and nothing forces the halves.
     */
    public boolean drawsWholeBox() {
        return !forceSplitDraw && straight();
    }

    /** True when {@code child} is the tip {@link #split} cut from this part's box. */
    public boolean isSplitTip(ModelPart child) {
        return child != null && child == straightChild;
    }

    public ModelPart(String name) {
        this.name = name;
    }

    public ModelPart pivot(float x, float y, float z) {
        pivotX = x;
        pivotY = y;
        pivotZ = z;
        return this;
    }

    public ModelPart box(float cx, float cy, float cz, float sx, float sy, float sz) {
        boxX = cx;
        boxY = cy;
        boxZ = cz;
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        return this;
    }

    public ModelPart color(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }

    public ModelPart color(int rgb) {
        return color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    public ModelPart emissive(float e) {
        emissive = e;
        return this;
    }

    public ModelPart child(ModelPart c) {
        children.add(c);
        return this;
    }

    public void resetPose() {
        rotX = rotY = rotZ = 0;
        poseX = poseY = poseZ = 0;
        scale = 1f;
        visible = true;
        forceSplitDraw = false;
        for (ModelPart c : children) {
            c.resetPose();
        }
    }

    /**
     * Draws this part and children. For the color pass set colorPass=true (uses
     * uColor/uEmissive); the shadow pass only needs uModel.
     */
    public int render(Matrix4f parent, ShaderProgram shader, Mesh centeredCube, boolean colorPass) {
        return render(parent, shader, centeredCube, colorPass, false);
    }

    private int render(Matrix4f parent, ShaderProgram shader, Mesh centeredCube,
                       boolean colorPass, boolean skipBox) {
        if (!visible) {
            return 0;
        }
        int draws = 0;
        local.set(parent)
                .translate(pivotX + poseX, pivotY + poseY, pivotZ + poseZ)
                .rotateZ(rotZ).rotateY(rotY).rotateX(rotX);
        if (scale != 1f) {
            local.scale(scale);
        }
        boolean merged = drawsWholeBox();
        if (sizeX > 0 && !skipBox) {
            if (merged) {
                draw.set(local).translate(wholeX, wholeY, wholeZ).scale(wholeSizeX, wholeSizeY, wholeSizeZ);
            } else {
                draw.set(local).translate(boxX, boxY, boxZ).scale(sizeX, sizeY, sizeZ);
            }
            shader.set("uModel", draw);
            if (colorPass) {
                shader.set("uColor", r, g, b);
                shader.set("uEmissive", emissive);
            }
            centeredCube.draw();
            draws++;
        }
        for (ModelPart c : children) {
            draws += c.render(local, shader, centeredCube, colorPass, merged && c == straightChild);
        }
        return draws;
    }

    /** Depth-first search by part name. */
    public ModelPart find(String partName) {
        if (name.equals(partName)) {
            return this;
        }
        for (ModelPart c : children) {
            ModelPart hit = c.find(partName);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }
}
