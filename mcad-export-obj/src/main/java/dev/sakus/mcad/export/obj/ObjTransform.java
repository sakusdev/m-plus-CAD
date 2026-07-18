/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.export.obj;

import dev.sakus.mcad.api.Quaterniond;
import dev.sakus.mcad.api.Transform;
import dev.sakus.mcad.api.Vec3d;

record ObjTransform(Matrix3 linear, Vec3d translation) {
    static ObjTransform identity() {
        return new ObjTransform(Matrix3.identity(), Vec3d.ZERO);
    }

    static ObjTransform fromTransform(Transform transform) {
        Matrix3 rotation = Matrix3.fromQuaternion(transform.rotation());
        Matrix3 scale = Matrix3.diagonal(
                transform.scale().x(), transform.scale().y(), transform.scale().z());
        return new ObjTransform(rotation.multiply(scale), transform.translation());
    }

    static ObjTransform global(ObjExportSettings settings) {
        Matrix3 rotation = Matrix3.eulerXyz(settings.rotationDegrees());
        Matrix3 scale = Matrix3.diagonal(settings.unitScale(), settings.unitScale(), settings.unitScale());
        Matrix3 axis = switch (settings.targetAxis()) {
            case INTERNAL_RIGHT_HANDED_Y_UP -> Matrix3.identity();
            case RIGHT_HANDED_Z_UP -> new Matrix3(
                    1, 0, 0,
                    0, 0, -1,
                    0, 1, 0);
            case LEFT_HANDED_Y_UP -> new Matrix3(
                    1, 0, 0,
                    0, 1, 0,
                    0, 0, -1);
        };
        Matrix3 linear = axis.multiply(scale).multiply(rotation);
        Vec3d origin = settings.originOffset();
        Vec3d translation = linear.apply(new Vec3d(-origin.x(), -origin.y(), -origin.z()));
        return new ObjTransform(linear, translation);
    }

    ObjTransform after(ObjTransform inner) {
        Matrix3 combinedLinear = linear.multiply(inner.linear);
        Vec3d combinedTranslation = add(linear.apply(inner.translation), translation);
        return new ObjTransform(combinedLinear, combinedTranslation);
    }

    Vec3d applyPoint(Vec3d point) {
        return add(linear.apply(point), translation);
    }

    Vec3d applyNormal(Vec3d normal) {
        Vec3d transformed = linear.inverseTranspose().apply(normal);
        double length = Math.sqrt(
                transformed.x() * transformed.x()
                        + transformed.y() * transformed.y()
                        + transformed.z() * transformed.z());
        if (!(length > 0.0) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("normal transform produced a non-finite or zero vector");
        }
        return new Vec3d(
                transformed.x() / length,
                transformed.y() / length,
                transformed.z() / length);
    }

    double determinant() {
        return linear.determinant();
    }

    void validateForNormals() {
        linear.inverseTranspose();
    }

    private static Vec3d add(Vec3d left, Vec3d right) {
        return new Vec3d(left.x() + right.x(), left.y() + right.y(), left.z() + right.z());
    }

    record Matrix3(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22) {
        Matrix3 {
            double[] values = {m00, m01, m02, m10, m11, m12, m20, m21, m22};
            for (double value : values) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("transform matrix contains a non-finite value");
                }
            }
        }

        static Matrix3 identity() {
            return diagonal(1.0, 1.0, 1.0);
        }

        static Matrix3 diagonal(double x, double y, double z) {
            return new Matrix3(x, 0, 0, 0, y, 0, 0, 0, z);
        }

        static Matrix3 fromQuaternion(Quaterniond quaternion) {
            double magnitude = Math.sqrt(
                    quaternion.x() * quaternion.x()
                            + quaternion.y() * quaternion.y()
                            + quaternion.z() * quaternion.z()
                            + quaternion.w() * quaternion.w());
            double x = quaternion.x() / magnitude;
            double y = quaternion.y() / magnitude;
            double z = quaternion.z() / magnitude;
            double w = quaternion.w() / magnitude;
            return new Matrix3(
                    1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                    2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                    2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y));
        }

        static Matrix3 eulerXyz(Vec3d degrees) {
            double x = Math.toRadians(degrees.x());
            double y = Math.toRadians(degrees.y());
            double z = Math.toRadians(degrees.z());
            Matrix3 rotateX = new Matrix3(
                    1, 0, 0,
                    0, Math.cos(x), -Math.sin(x),
                    0, Math.sin(x), Math.cos(x));
            Matrix3 rotateY = new Matrix3(
                    Math.cos(y), 0, Math.sin(y),
                    0, 1, 0,
                    -Math.sin(y), 0, Math.cos(y));
            Matrix3 rotateZ = new Matrix3(
                    Math.cos(z), -Math.sin(z), 0,
                    Math.sin(z), Math.cos(z), 0,
                    0, 0, 1);
            return rotateZ.multiply(rotateY).multiply(rotateX);
        }

        Matrix3 multiply(Matrix3 other) {
            return new Matrix3(
                    m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
                    m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
                    m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
                    m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
                    m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
                    m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
                    m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
                    m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
                    m20 * other.m02 + m21 * other.m12 + m22 * other.m22);
        }

        Vec3d apply(Vec3d vector) {
            return new Vec3d(
                    m00 * vector.x() + m01 * vector.y() + m02 * vector.z(),
                    m10 * vector.x() + m11 * vector.y() + m12 * vector.z(),
                    m20 * vector.x() + m21 * vector.y() + m22 * vector.z());
        }

        double determinant() {
            return m00 * (m11 * m22 - m12 * m21)
                    - m01 * (m10 * m22 - m12 * m20)
                    + m02 * (m10 * m21 - m11 * m20);
        }

        Matrix3 inverseTranspose() {
            double determinant = determinant();
            if (determinant == 0.0 || !Double.isFinite(determinant)) {
                throw new IllegalArgumentException("transform is singular or non-finite");
            }
            double inverse = 1.0 / determinant;
            return new Matrix3(
                    (m11 * m22 - m12 * m21) * inverse,
                    (m12 * m20 - m10 * m22) * inverse,
                    (m10 * m21 - m11 * m20) * inverse,
                    (m02 * m21 - m01 * m22) * inverse,
                    (m00 * m22 - m02 * m20) * inverse,
                    (m01 * m20 - m00 * m21) * inverse,
                    (m01 * m12 - m02 * m11) * inverse,
                    (m02 * m10 - m00 * m12) * inverse,
                    (m00 * m11 - m01 * m10) * inverse);
        }
    }
}
