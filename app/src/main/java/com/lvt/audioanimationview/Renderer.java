package com.lvt.audioanimationview;

import android.graphics.Canvas;

abstract public class Renderer {

    public Renderer() {

    }

    abstract public void onRender(Canvas canvas, byte[] data, int width, int height);

    public void render(Canvas canvas, byte[] data, int width, int height) {

        onRender(canvas, data, width, height);
    }

    public abstract void drawBackground(Canvas mCanvas, int width, int height);
}
