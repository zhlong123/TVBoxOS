package com.github.tvbox.osc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.R;

import org.json.JSONException;
import org.json.JSONObject;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 图片工具
 * @version 1.0.0 <br/>
 */
public class ImgUtil {
    private static final LruCache<String, Drawable> POSTER_FALLBACK_CACHE = new LruCache<>(48);

    public static boolean isBase64Image(String picUrl) {
        return picUrl.startsWith("data:image");
    }
    public static int defaultWidth = 160;
    public static int defaultHeight = 210;
    public static int searchCardWidth = 145;
    public static int searchCardHeight = 193;

    /**
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    public static Style initStyle()
    {
        String bStyle = ApiConfig.get().getHomeSourceBean().getStyle();
        if(!bStyle.isEmpty()){
            try {
                JSONObject jsonObject = new JSONObject(bStyle);
                float ratio = (float) jsonObject.getDouble("ratio");
                String type = jsonObject.getString("type");
                return new Style(ratio, type);
            }catch (JSONException e){

            }
        }
        return null;
    }

    public static int spanCountByStyle(Style style,int defaultCount){
        int spanCount=defaultCount;
        if ("rect".equals(style.type)) {
            if (style.ratio >= 1.7) {
                spanCount = 4; // 横图
            } else if (style.ratio >= 1.3) {
                spanCount = 5; // 4:3
            }
        } else if ("list".equals(style.type)) {
            spanCount = 1;
        }
        return spanCount;
    }

    public static int getStyleDefaultWidth(Style style){
        int styleDefaultWidth = 231;
        if(style.ratio<1)styleDefaultWidth=177;
        if(style.ratio>1.7)styleDefaultWidth=314;
        return styleDefaultWidth;
    }

    public static Bitmap decodeBase64ToBitmap(String base64Str) {
        String base64Data = base64Str.substring(base64Str.indexOf(",") + 1);
        byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    /** 无封面 / 加载失败：柔和渐变 + 片名前两字（低对比，不抢焦点）。 */
    public static Drawable createTextDrawable(String text) {
        return createPosterFallback(text, UiLayoutConfig.DEFAULT_CARD_WIDTH_MM, UiLayoutConfig.DEFAULT_CARD_HEIGHT_MM);
    }

    public static Drawable createPosterFallback(String name, int widthMm, int heightMm) {
        String label = posterLabel(name);
        String cacheKey = label + "@" + widthMm + "x" + heightMm;
        Drawable cached = POSTER_FALLBACK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        int width = AutoSizeUtils.mm2px(App.getInstance(), widthMm);
        int height = AutoSizeUtils.mm2px(App.getInstance(), heightMm);
        if (width <= 0) width = AutoSizeUtils.mm2px(App.getInstance(), defaultWidth);
        if (height <= 0) height = AutoSizeUtils.mm2px(App.getInstance(), defaultHeight);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float radius = AutoSizeUtils.mm2px(App.getInstance(), 12);

        int hash = Math.abs(name != null ? name.hashCode() : 0);
        int topColor = shiftColor(0xFF1E2430, hash, 10);
        int bottomColor = shiftColor(0xFF12161E, hash, 8);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, 0, height, topColor, bottomColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height), radius, radius, fill);

        float inset = AutoSizeUtils.mm2px(App.getInstance(), 1.5f);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(AutoSizeUtils.mm2px(App.getInstance(), 0.6f));
        stroke.setColor(0x14FFFFFF);
        canvas.drawRoundRect(new RectF(inset, inset, width - inset, height - inset), radius * 0.85f, radius * 0.85f, stroke);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0x42F3EFE6);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(false);
        float textSize = width * 0.22f;
        if (label.length() > 1) {
            textSize = width * 0.18f;
        }
        textPaint.setTextSize(textSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = height * 0.52f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, width / 2f, textY, textPaint);

        Drawable drawable = new BitmapDrawable(App.getInstance().getResources(), bitmap);
        POSTER_FALLBACK_CACHE.put(cacheKey, drawable);
        return drawable;
    }

    private static String posterLabel(String name) {
        if (TextUtils.isEmpty(name)) {
            return "—";
        }
        String cleaned = name.replaceAll("[【\\[（(].*?[】\\]）)]", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = name.trim();
        }
        if (cleaned.isEmpty()) {
            return "—";
        }
        int end = Math.min(cleaned.length(), 2);
        return cleaned.substring(0, end);
    }

    private static int shiftColor(int color, int hash, int range) {
        int shift = (hash % (range * 2 + 1)) - range;
        int a = Color.alpha(color);
        int r = clamp(Color.red(color) + shift);
        int g = clamp(Color.green(color) + shift / 2);
        int b = clamp(Color.blue(color) + shift);
        return Color.argb(a, r, g, b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static void clearCache() {
        POSTER_FALLBACK_CACHE.evictAll();
    }
}
