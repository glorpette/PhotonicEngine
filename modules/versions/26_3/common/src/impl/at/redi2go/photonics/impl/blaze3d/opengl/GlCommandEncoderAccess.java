package at.redi2go.photonics.impl.blaze3d.opengl;

import java.lang.reflect.Field;

public final class GlCommandEncoderAccess {
    private static final String DEVICE_FIELD = "device";

    private GlCommandEncoderAccess() {
    }

    public static GlDeviceAccessor device(Object commandEncoder) {
        try {
            Field field = commandEncoder.getClass().getDeclaredField(DEVICE_FIELD);
            field.setAccessible(true);
            return (GlDeviceAccessor) field.get(commandEncoder);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not access RenderPearl's GL device", error);
        }
    }
}
