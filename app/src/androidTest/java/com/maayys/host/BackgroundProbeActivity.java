package com.maayys.host;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Pure Java: a standalone process cannot borrow Kotlin from the target APK. */
public class BackgroundProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private boolean wasTapped;
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.rgb(20,33,61));
                paint.setColor(wasTapped ? Color.rgb(245,158,11) : Color.rgb(33,199,141));
                canvas.drawRect(100,100,400,300,paint);
                paint.setColor(Color.WHITE); paint.setTextSize(40);
                canvas.drawText("MaaYYs background input test",100,70,paint);
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP && e.getX() >= 100 && e.getX() <= 400 && e.getY() >= 100 && e.getY() <= 300) {
                    wasTapped = true; invalidate(); performClick();
                }
                return true;
            }
            @Override public boolean performClick() { super.performClick(); return true; }
        });
    }
}
