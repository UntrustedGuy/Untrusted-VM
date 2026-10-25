package android.graphics;

public class Bitmap {
    public enum Config {
        ARGB_8888,
        RGB_565
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap();
    }

    public void recycle() {}
}
