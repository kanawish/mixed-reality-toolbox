package com.kanawish.dd.thing;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GvrArmModel {
    private static final Vector3f FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);
    private static final Vector3f UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f POINTER_OFFSET = new Vector3f(0.0f, -0.009f, -0.109f);
    private static final Vector3f DEFAULT_SHOULDER_RIGHT = new Vector3f(0.19f, -0.19f, 0.03f);
    private static final Vector3f ELBOW_MIN_RANGE = new Vector3f(-0.05f, -0.1f, -0.2f);
    private static final Vector3f ELBOW_MAX_RANGE = new Vector3f(0.05f, 0.1f, 0.0f);
    private static final float GRAVITY_FORCE = 9.807f;
    private static final float DELTA_ALPHA = 4.0f;
    private static final float VELOCITY_FILTER_SUPPRESS = 0.99f;
    private static final float GRAVITY_CALIB_STRENGTH = 0.999f;
    private static final float MIN_ACCEL = 1.0f;

    public static class UpdateData {
        final boolean connected;
        final Vector3f acceleration;
        final Quaternionf orientation;
        final Vector3f gyro;
        final Vector3f headDirection;
        final Vector3f headPosition;
        final float deltaTimeSeconds;

        public UpdateData(boolean connected, Vector3f acceleration, Quaternionf orientation, Vector3f gyro, Vector3f headDirection, Vector3f headPosition, float deltaTimeSeconds) {
            this.connected = connected;
            this.acceleration = acceleration;
            this.orientation = orientation;
            this.gyro = gyro;
            this.headDirection = headDirection;
            this.headPosition = headPosition;
            this.deltaTimeSeconds = deltaTimeSeconds;
        }
    }

    public enum GazeBehavior {
        Never,
        DuringMotion,
        Always
    }

    public enum Handedness {
        Right,
        Left,
        Unknown
    }

    private float addedElbowHeight = 0.0f;
    private float addedElbowDepth = 0.0f;
    private float pointerTiltAngle = 15.0f;

    // This is default in original code but I can't use it correctly.
    // It requires UpdateData.gyro but I couldn't pass correct values.
    // So I use Always instead.
    //    private GazeBehavior followGaze = DuringMotion;
    private GazeBehavior followGaze = GazeBehavior.Always;
    private Handedness handedness = Handedness.Right;
    private boolean useAccelerometer = false;
    private float fadeDistanceFromFace = 0.32f;
    private float tooltipMinDistanceFromFace = 0.45f;

    private final Vector3f wristPosition = new Vector3f();
    private final Quaternionf wristRotation = new Quaternionf();

    private final Vector3f elbowPosition = new Vector3f();
    private final Quaternionf elbowRotation = new Quaternionf();

    private final Vector3f shoulderPosition = new Vector3f();
    private final Quaternionf shoulderRotation = new Quaternionf();

    private final Vector3f elbowOffset = new Vector3f();
    private final Vector3f torsoDirection = new Vector3f();
    private final Vector3f filteredVelocity = new Vector3f();
    private final Vector3f filteredAccel = new Vector3f();
    private final Vector3f zeroAccel = new Vector3f(0.0f, GRAVITY_FORCE, 0.0f);
    private final Vector3f handedMultiplier = new Vector3f();
    private float controllerAlphaValue = 1.0f;
    private float tooltipAlphaValue = 0.0f;

    private boolean firstUpdate = true;

    public GvrArmModel() {
        updateHandedness();
    }

    public Vector3f getControllerPosition() {
        return wristPosition;
    }

    public Quaternionf getControllerRotation() {
        return wristRotation;
    }

    public Vector3f getPointerPositionOffset() {
        return POINTER_OFFSET;
    }

    public float getAddedElbowHeight() {
        return addedElbowHeight;
    }

    public void setAddedElbowHeight(float elbowHeight) {
        addedElbowHeight = elbowHeight;
    }

    public float getAddedElbowDepth() {
        return addedElbowDepth;
    }

    public void setAddedElbowDepth(float elbowDepth) {
        addedElbowDepth = elbowDepth;
    }

    public float getPointerTiltAngle() {
        return pointerTiltAngle;
    }

    public void setPointerTiltAngle(float tiltAngle) {
        pointerTiltAngle = tiltAngle;
    }

    public GazeBehavior getGazeBehavior() {
        return followGaze;
    }

    public void setGazeBehavior(GazeBehavior gazeBehavior) {
        followGaze = gazeBehavior;
    }

    public Handedness getHandedness() {
        return handedness;
    }

    public void setHandedness(Handedness newHandedness) {
        handedness = newHandedness;
    }

    public boolean getUseAccelerometer() {
        return useAccelerometer;
    }

    public void setUseAccelerometer(boolean newUseAccelerometer) {
        useAccelerometer = newUseAccelerometer;
    }

    public float getFadeDistanceFromFace() {
        return fadeDistanceFromFace;
    }

    public void setFadeDistanceFromFace(float distanceFromFace) {
        fadeDistanceFromFace = distanceFromFace;
    }

    public float getTooltipMinDistanceFromFace() {
        return tooltipMinDistanceFromFace;
    }

    public void setTooltipMinDistanceFromFace(float distanceFromFace) {
        tooltipMinDistanceFromFace = distanceFromFace;
    }

    public float getControllerAlphaValue() {
        return controllerAlphaValue;
    }

    public float getTooltipAlphaValue() {
        return tooltipAlphaValue;
    }

    private void updateHandedness() {
        // Place the shoulder in anatomical positions based on the height and handedness.
        handedMultiplier.set(0.0f, 1.0f, 1.0f);
        if (handedness == Handedness.Right) {
            handedMultiplier.x = 1.0f;
        } else if (handedness == Handedness.Left) {
            handedMultiplier.x = -1.0f;
        }

        // Place the shoulder in anatomical positions based on the height and handedness.
        shoulderRotation.identity();
        shoulderPosition.set(DEFAULT_SHOULDER_RIGHT);
        shoulderPosition.mul(handedMultiplier);
    }

    public void update(UpdateData updateData) {
        updateHandedness();
        updateTorsoDirection(updateData);

        if (updateData.connected) {
            updateFromController(updateData);
        } else {
            resetState();
        }

        if (useAccelerometer) {
            updateVelocity(updateData);
            transformElbow(updateData);
        } else {
            elbowOffset.zero();
        }

        applyArmModel(updateData);
        updateTransparency(updateData);
    }

    private void updateTorsoDirection(UpdateData updateData) {
        // Ignore updates here if requested.
        if (followGaze == GazeBehavior.Never) {
            return;
        }

        // Determine the gaze direction horizontally.
        Vector3f headDirection = new Vector3f(updateData.headDirection);
        headDirection.y = 0.0f;
        headDirection.normalize();

        if (followGaze == GazeBehavior.Always) {
            torsoDirection.set(headDirection);
        } else if (followGaze == GazeBehavior.DuringMotion) {
            float angularVelocity = updateData.gyro.length();
            float gazeFilterStrength = clampf((angularVelocity - 0.2f) / 45.0f, 0.0f, 0.1f);
            torsoDirection.lerp(headDirection, gazeFilterStrength, torsoDirection);
        }

        // Rotate the fixed joints.
        Quaternionf gazeRotation = new Quaternionf().rotationTo(FORWARD, torsoDirection);
        shoulderRotation.set(gazeRotation);
        shoulderPosition.rotate(gazeRotation);
    }

    private void updateFromController(UpdateData updateData) {
        // Get the orientation-adjusted acceleration.
        Vector3f Accel = updateData.acceleration.rotate(updateData.orientation, new Vector3f());

        // Very slowly calibrate gravity force out of acceleration.
//        zeroAccel = zeroAccel * GRAVITY_CALIB_STRENGTH + Accel * (1.0f - GRAVITY_CALIB_STRENGTH);
        Vector3f a = zeroAccel.mul(GRAVITY_CALIB_STRENGTH, new Vector3f());
        Vector3f b = Accel.mul((1.0f - GRAVITY_CALIB_STRENGTH), new Vector3f());
        a.add(b, zeroAccel);

        Accel.sub(zeroAccel, filteredAccel);

        // If no tracking history, reset the velocity.
        if (firstUpdate) {
            filteredVelocity.zero();
            firstUpdate = false;
        }

        // IMPORTANT: The accelerometer is not reliable at these low magnitudes
        // so ignore it to prevent drift.
        if (filteredAccel.length() < MIN_ACCEL) {
            // Suppress the acceleration.
            filteredAccel.zero();
            filteredVelocity.mul(0.9f);
        } else {
            // If the velocity is decreasing, prevent snap-back by reducing deceleration.
//            Vector3f newVelocity = filteredVelocity + filteredAccel * updateData.deltaTimeSeconds;
            Vector3f c = filteredAccel.mul(updateData.deltaTimeSeconds, new Vector3f());
            Vector3f newVelocity = filteredVelocity.add(c, new Vector3f());

            if (newVelocity.lengthSquared() < filteredVelocity.lengthSquared()) {
                filteredAccel.mul(0.5f);
            }
        }
    }

    private void updateVelocity(UpdateData updateData) {
        // Update the filtered velocity.
        Vector3f a = filteredAccel.mul(updateData.deltaTimeSeconds, new Vector3f());
        filteredVelocity.add(a);
        filteredVelocity.mul(VELOCITY_FILTER_SUPPRESS);
    }

    private void transformElbow(UpdateData updateData) {
        // Apply the filtered velocity to update the elbow offset position.
        if (useAccelerometer) {
            elbowOffset.add(filteredVelocity.mul(updateData.deltaTimeSeconds, new Vector3f()));
            elbowOffset.x = (clampf(elbowOffset.x(), ELBOW_MIN_RANGE.x(), ELBOW_MAX_RANGE.x()));
            elbowOffset.y = (clampf(elbowOffset.y(), ELBOW_MIN_RANGE.y(), ELBOW_MAX_RANGE.y()));
            elbowOffset.z = (clampf(elbowOffset.z(), ELBOW_MIN_RANGE.z(), ELBOW_MAX_RANGE.z()));
        }
    }

    private void applyArmModel(UpdateData updateData) {
        // Find the controller's orientation relative to the player
        Quaternionf controllerOrientation = updateData.orientation;
        controllerOrientation = shoulderRotation.invert(new Quaternionf()).mul(controllerOrientation);

        // Get the relative positions of the joints
        elbowPosition.set(0.195f, -0.5f + addedElbowHeight, 0.075f + addedElbowDepth);
        elbowPosition.mul(handedMultiplier).add(elbowOffset);
        wristPosition.set(0.0f, 0.0f, -0.25f).mul(handedMultiplier);
        Vector3f armExtensionOffset = new Vector3f(-0.13f, 0.14f, -0.08f).mul(handedMultiplier);

        // Extract just the x rotation angle
        Vector3f controllerForward = controllerOrientation.transform(FORWARD, new Vector3f());
        float xAngle = (float) (90.0f - Math.toDegrees(controllerForward.angle(UP)));

        // Remove the z rotation from the controller
        Quaternionf xyRotation = new Quaternionf().rotationTo(FORWARD, controllerForward);

        // Offset the elbow by the extension
        float MIN_EXTENSION_ANGLE = 7.0f;
        float MAX_EXTENSION_ANGLE = 60.0f;
        float normalizedAngle = (xAngle - MIN_EXTENSION_ANGLE) / (MAX_EXTENSION_ANGLE - MIN_EXTENSION_ANGLE);
        float extensionRatio = clampf(normalizedAngle, 0.0f, 1.0f);
        if (!useAccelerometer) {
            elbowPosition.add(armExtensionOffset.mul(extensionRatio, new Vector3f()));
        }

        // Calculate the lerp interpolation factor
        float EXTENSION_WEIGHT = 0.4f;
        float totalAngle = angleDegrees(xyRotation, new Quaternionf());
        float lerpSuppresion = (float) (1.0f - Math.pow(totalAngle / 180.0f, 6));
        float lerpValue = lerpSuppresion * (0.4f + 0.6f * extensionRatio * EXTENSION_WEIGHT);

        // Apply the absolute rotations to the joints
        Quaternionf lerpRotation = new Quaternionf().slerp(xyRotation, lerpValue);
        shoulderRotation.mul(lerpRotation.invert(new Quaternionf()).mul(controllerOrientation), elbowRotation);
        shoulderRotation.mul(controllerOrientation, wristRotation);

        // Determine the relative positions
        shoulderRotation.transform(elbowPosition);
        elbowPosition.add(elbowRotation.transform(wristPosition, new Vector3f()), wristPosition);
    }

    private void updateTransparency(UpdateData updateData) {
        // Determine how vertical the controller is pointing.
        float distanceToFace = wristPosition.length();
        if (distanceToFace < fadeDistanceFromFace) {
            controllerAlphaValue = clampf(controllerAlphaValue - DELTA_ALPHA * updateData.deltaTimeSeconds, 0.0f, 1.0f);
        } else {
            controllerAlphaValue = clampf(controllerAlphaValue + DELTA_ALPHA * updateData.deltaTimeSeconds, 0.0f, 1.0f);
        }

        if (distanceToFace < fadeDistanceFromFace || distanceToFace > tooltipMinDistanceFromFace) {
            tooltipAlphaValue = clampf(tooltipAlphaValue - DELTA_ALPHA * updateData.deltaTimeSeconds, 0.0f, 1.0f);
        } else {
            tooltipAlphaValue = clampf(tooltipAlphaValue + DELTA_ALPHA * updateData.deltaTimeSeconds, 0.0f, 1.0f);
        }
    }

    private void resetState() {
        // We've lost contact, quickly reset the state.
        filteredVelocity.mul(0.5f, filteredVelocity);
        filteredAccel.mul(0.5f, filteredAccel);
        firstUpdate = true;
    }

    private static float clampf(float value, float min, float max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }

    private static float angleDegrees(Quaternionf a, Quaternionf b) {
        return (b.mul(a.invert(new Quaternionf()))).w();
    }
}
