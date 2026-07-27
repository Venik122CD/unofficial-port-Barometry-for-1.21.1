package dev.ianaduarte.barometry;

import org.joml.Matrix4f;

public interface ProjectionGetter {
    Matrix4f getProjectionMatrix(float farPlane, float partialTicks);
}