package finance.dsf.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

final class CounterViews {
    static void bind(Context context,RemoteViews views,int width,String tvl,String apy,
        String previousTvl,String previousApy,boolean animate) {
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(WidgetFont.regular(context));paint.setFontFeatureSettings("tnum");
        float density=context.getResources().getDisplayMetrics().density;
        float size=width<300?18:20;
        // Include per-glyph pixel rounding and a clear gap between the two metrics.
        for(int i=0;i<3;i++) {
            float required=metricWidth(paint,"TVL",tvl,size,density)+metricWidth(paint,"APY",apy,size,density)+12+20;
            if(required<=width-32)break;
            size*=(width-32)/required;
        }
        boolean dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int muted=dark?0xffabb8d1:0xff52617a,ink=dark?0xffeef2fc:0xff172137;
        views.setImageViewBitmap(R.id.tvl_label,glyph("TVL",size*.72f,size,paint,density,muted));
        views.setImageViewBitmap(R.id.apy_label,glyph("APY",size*.72f,size,paint,density,muted));
        fill(context,views,R.id.tvl_digits,RollingDigits.cells(previousTvl,tvl,animate),size,paint,density,ink);
        fill(context,views,R.id.apy_digits,RollingDigits.cells(previousApy,apy,animate),size,paint,density,ink);
    }
    private static float metricWidth(Paint p,String label,String value,float size,float density) {
        p.setTextSize(size*.72f);
        float width=(float)Math.ceil(p.measureText(label)*density)/density;
        p.setTextSize(size);
        for(char c:value.toCharArray())width+=(float)Math.ceil(p.measureText(String.valueOf(c))*density)/density;
        return width;
    }
    private static void fill(Context context,RemoteViews parent,int container,List<RollingDigits.Cell> cells,
        float size,Paint paint,float density,int color) {
        parent.removeAllViews(container);paint.setTextSize(size);
        for(RollingDigits.Cell cell:cells) {
            int layout=cell.direction()>0?R.layout.counter_up:cell.direction()<0?R.layout.counter_down:R.layout.counter_static;
            RemoteViews view=new RemoteViews(context.getPackageName(),layout);
            view.setImageViewBitmap(R.id.counter_after,glyph(cell.after(),size,size,paint,density,color));
            view.setContentDescription(R.id.counter_after,cell.after());
            if(cell.direction()!=0) {
                view.setImageViewBitmap(R.id.counter_before,glyph(cell.before(),size,size,paint,density,color));
                // Absolute child index: reapplying RemoteViews can never flip back to the old digit.
                view.setInt(R.id.counter_flipper,"setDisplayedChild",1);
                // A freshly inflated outgoing child must receive layout before its first frame.
                // Its fillAfter translation keeps it clipped outside the cell when the roll ends.
                view.setViewVisibility(R.id.counter_before,View.VISIBLE);
            }
            parent.addView(container,view);
        }
    }
    private static Bitmap glyph(String value,float textSize,float valueSize,Paint paint,float density,int color) {
        // Drawing the bundled font here avoids launcher restrictions/replacements of custom TextView fonts.
        paint.setTextSize(valueSize);Paint.FontMetrics metrics=paint.getFontMetrics();
        float baseline=(32-metrics.ascent-metrics.descent)/2;
        paint.setTextSize(textSize);paint.setColor(color);
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(paint.measureText(value)*density)),Math.round(32*density),Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(160*density));
        Canvas canvas=new Canvas(bitmap);canvas.scale(density,density);
        canvas.drawText(value,0,baseline,paint);
        return bitmap;
    }
}
