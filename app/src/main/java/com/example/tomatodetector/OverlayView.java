package com.example.tomatodetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class OverlayView extends View {
    private Paint paint;
    private int currentColor = Color.BLUE; // cor padrão

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8);
        paint.setColor(currentColor);
    }

    /**
     * Define a cor do quadrado e força a atualização da tela
     */
    public void setBoxColor(int color) {
        this.currentColor = color;
        Log.d("OverlayView", "Cor do quadrado alterada para: #" + Integer.toHexString(color));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int boxSize = Math.min(width, height) / 2;
        int left = (width - boxSize) / 2;
        int top = (height - boxSize) / 2;

        paint.setColor(currentColor);
        canvas.drawRect(left, top, left + boxSize, top + boxSize, paint);
    }
}
