package com.veylon.gfx.model;

import com.veylon.engine.Mesh;
import com.veylon.engine.ShaderProgram;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** A part hierarchy plus a name index. One instance per species, re-posed per entity. */
public class EntityModel {

    public final ModelPart root;
    private final Map<String, ModelPart> index = new HashMap<>();

    public EntityModel(ModelPart root) {
        this.root = root;
        indexParts(root);
    }

    private void indexParts(ModelPart p) {
        index.put(p.name, p);
        for (ModelPart c : p.children) {
            indexParts(c);
        }
    }

    /** Named part lookup; returns a hidden dummy for unknown names so poses never NPE. */
    public ModelPart part(String name) {
        ModelPart p = index.get(name);
        if (p == null) {
            p = new ModelPart(name);
            p.visible = false;
            index.put(name, p);
        }
        return p;
    }

    public void resetPose() {
        root.resetPose();
    }

    /** Renders the hierarchy and returns the number of cuboid draw submissions. */
    public int render(Matrix4f base, ShaderProgram shader, Mesh centeredCube, boolean colorPass) {
        return root.render(base, shader, centeredCube, colorPass);
    }
}
