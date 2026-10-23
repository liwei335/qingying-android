package io.qingying.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(16,20,23), CARD = Color.rgb(27,34,38), TEXT = Color.rgb(237,244,240);
    static final int MUTED = Color.rgb(152,170,166), GREEN = Color.rgb(112,225,191), GOLD = Color.rgb(242,205,133);
    static int dp(Context c, float n) { return (int)(c.getResources().getDisplayMetrics().density * n + .5f); }
    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false);
        return t;
    }
    static TextView title(Context c, String value, int size) {
        TextView t = text(c, value, size, TEXT); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static void pad(View v, int x, int y) { v.setPadding(dp(v.getContext(),x),dp(v.getContext(),y),dp(v.getContext(),x),dp(v.getContext(),y)); }
    static TextView button(Context c, String label, boolean primary, View.OnClickListener action) {
        TextView t = text(c,label,14,primary?BG:TEXT); t.setGravity(Gravity.CENTER); pad(t,16,12);
        t.setMinHeight(dp(c,48)); t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3370E1BF),shape(c,primary?GREEN:CARD,14),null));
        t.setOnClickListener(action); t.setFocusable(true); return t;
    }
    static LinearLayout.LayoutParams margin(Context c, int width, int height, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width < 0 ? width : dp(c,width),height < 0 ? height : dp(c,height));
        p.topMargin = dp(c,top); return p;
    }
}
