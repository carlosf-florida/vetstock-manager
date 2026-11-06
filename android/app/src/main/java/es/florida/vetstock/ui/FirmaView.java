package es.florida.vetstock.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;

/** Lienzo para firmar con el dedo. Genera el mismo PNG en Base64 que la web. */
public class FirmaView extends View {
    private final Path trazo = new Path();
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ultimaX, ultimaY;
    private boolean dibujada;

    public FirmaView(Context contexto, AttributeSet atributos) {
        super(contexto, atributos);
        pincel.setColor(Color.BLACK);
        pincel.setStyle(Paint.Style.STROKE);
        pincel.setStrokeCap(Paint.Cap.ROUND);
        pincel.setStrokeJoin(Paint.Join.ROUND);
        pincel.setStrokeWidth(5f * contexto.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas lienzo) {
        super.onDraw(lienzo);
        lienzo.drawPath(trazo, pincel);
    }

    @Override
    public boolean onTouchEvent(MotionEvent evento) {
        // Que el ScrollView de la pantalla no se quede el gesto mientras se firma
        getParent().requestDisallowInterceptTouchEvent(evento.getAction() != MotionEvent.ACTION_UP);
        float x = evento.getX();
        float y = evento.getY();
        switch (evento.getAction()) {
            case MotionEvent.ACTION_DOWN:
                trazo.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                trazo.quadTo(ultimaX, ultimaY, (x + ultimaX) / 2, (y + ultimaY) / 2);
                dibujada = true;
                break;
            case MotionEvent.ACTION_UP:
                trazo.lineTo(x, y);
                performClick();
                break;
            default:
                return false;
        }
        ultimaX = x;
        ultimaY = y;
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void borrar() {
        trazo.reset();
        dibujada = false;
        invalidate();
    }

    public boolean tieneFirma() {
        return dibujada;
    }

    /** Fondo blanco y trazo negro, como data URL. */
    public String aDataUrlPng() {
        Bitmap imagen = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas lienzo = new Canvas(imagen);
        lienzo.drawColor(Color.WHITE);
        lienzo.drawPath(trazo, pincel);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        imagen.compress(Bitmap.CompressFormat.PNG, 100, salida);
        imagen.recycle();
        return "data:image/png;base64," + Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP);
    }
}
