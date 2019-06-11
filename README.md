# Cam2Lib
An android library allowing the usage of camera2 API easily without the much boilerplate code.  

## Usage  
To use the library, we need to create an object of the Cam2Lib class and then provide a TextureView to draw on it.  
~~~
Cam2Lib cam2Lib = new Cam2Lib(context, cam2libCallback);
cam2Lib.open(findViewById(R.id.texv_capture), CameraDevice.TEMPLATE_PREVIEW);
~~~  

It is to be noted that we need to pass Cam2LibCallback. This will contain the callbacks about the states in which the library is currently in. It also provides the method using which we can receive the captured image.  

~~~   
public interface Cam2LibCallback {
    void onReady();
    void onComplete();
    void onImage(Image image);
    void onError(Throwable throwable);
}
~~~

Open done, we can all the startPreview method and preview the camera content on the TextureView provided.  
~~~
cam2Lib.startPreview();
~~~  

Once we need to take the picture from the camera we need to call the `getImage()` method. It will provide us the `Image` object in the `onImage` callback. In here we can use the `Cam2LibConverter` class to convert it to a bitmap.  

Once everything is completed, we need to call the `stopPreview` and the `close` method to perform the cleanups.
