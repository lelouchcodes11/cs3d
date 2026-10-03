package android.graphics;

public class Picture {
    private int mWidth;
    private int mHeight;

    public Picture() {
    }

    public Canvas beginRecording(int width, int height) {
        mWidth = width;
        mHeight = height;
        return new Canvas();
    }

    public void endRecording() {
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void draw(Canvas canvas) {
    }
}
