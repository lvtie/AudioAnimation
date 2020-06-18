package com.lvt.audioanimationview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

public class AudioAnimationView extends View {
    private static final String TAG = "AudioAnimationView";
    private byte[] mFFTBytes;
    private Rect mRect = new Rect();
    private Set<Renderer> mRenderers;
    private Paint mFadePaint = new Paint();
    Bitmap mCanvasBitmap;
    Canvas mCanvas;

    public AudioAnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        init();
    }

    public AudioAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public AudioAnimationView(Context context) {
        this(context, null, 0);
        init();
    }

    private void init() {
        mFFTBytes = null;
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
        mRenderers = new HashSet<Renderer>();
    }

    public void addRenderer(Renderer renderer) {
        if (renderer != null) {
            mRenderers.add(renderer);
        }
    }

    public void clearRenderers() {
        mRenderers.clear();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 && getHeight() == 0) {
            return;
        }
        // Create canvas once we're ready to draw
        mRect.set(0, 0, getWidth(), getHeight());

        if (mCanvasBitmap == null) {
            mCanvasBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Config.ARGB_8888);
        }
        if (mCanvas == null) {
            mCanvas = new Canvas(mCanvasBitmap);
        }
        mCanvas.drawPaint(mFadePaint);
        for (Renderer r : mRenderers) {
            if (mFFTBytes != null) {
                r.render(mCanvas, mFFTBytes, getWidth(), getHeight());
            } else {
                r.drawBackground(mCanvas, getWidth(), getHeight());
            }
        }
        canvas.drawBitmap(mCanvasBitmap, new Matrix(), null);
    }

    public void setmFFTBytes(byte[] bytes) {
        mFFTBytes = bytes;
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

}