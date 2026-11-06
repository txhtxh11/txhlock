package com.kidslock;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** 生成二维码位图；任何异常都返回 null，绝不让设置页崩。 */
public final class QrUtil {
    private QrUtil() {}

    public static Bitmap make(String text, int size) {
        if (text == null || size <= 0) return null;
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            int w = m.getWidth(), h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, w, 0, 0, w, h);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
