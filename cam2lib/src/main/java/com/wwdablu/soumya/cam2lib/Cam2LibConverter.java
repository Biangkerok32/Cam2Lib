package com.wwdablu.soumya.cam2lib;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.support.annotation.NonNull;

import java.nio.ByteBuffer;

public final class Cam2LibConverter {

    public static Bitmap toBitmap(@NonNull Image image, boolean displayScaled) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        BitmapFactory.Options options = null;

        if(!displayScaled) {
            options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inDensity = 0;
            options.inTargetDensity = 0;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
