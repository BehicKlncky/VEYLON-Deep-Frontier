package com.veylon.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    public final Vector3f position = new Vector3f();
    /** Degrees. Yaw 0 looks toward -Z. */
    public float yaw;
    public float pitch;
    public float fovDeg = 75f;

    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Vector3f front = new Vector3f();

    public Matrix4f viewMatrix() {
        view.identity()
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-position.x, -position.y, -position.z);
        return view;
    }

    public Matrix4f projectionMatrix(float aspect) {
        projection.identity().setPerspective((float) Math.toRadians(fovDeg), aspect, 0.06f, 320f);
        return projection;
    }

    /** Unit vector the camera is looking along. */
    public Vector3f front() {
        float cy = (float) Math.cos(Math.toRadians(yaw));
        float sy = (float) Math.sin(Math.toRadians(yaw));
        float cp = (float) Math.cos(Math.toRadians(pitch));
        float sp = (float) Math.sin(Math.toRadians(pitch));
        front.set(sy * cp, -sp, -cy * cp);
        return front;
    }
}
