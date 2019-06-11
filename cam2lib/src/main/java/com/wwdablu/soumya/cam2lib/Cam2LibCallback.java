package com.wwdablu.soumya.cam2lib;

import android.media.Image;

public interface Cam2LibCallback {
    void onReady();
    void onComplete();
    void onImage(Image image);
    void onError(Throwable throwable);
}
