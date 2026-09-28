package finance.dsf.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class ChartRenderer {
    private static final Locale RU=Locale.forLanguageTag("ru");
    static String money(Double value) { return value==null?"—":"$"+new DecimalFormat("#,##0",DecimalFormatSymbols.getInstance(RU)).format(value); }
    static String percent(Double value) { return value==null?"—":new DecimalFormat("0.00",DecimalFormatSymbols.getInstance(RU)).format(value)+"%"; }
    static Bitmap render(Context context,WidgetData data,int width,int height) {
        float scale=Math.min(2f,900f/width);
        Bitmap bitmap=Bitmap.createBitmap(Math.round(width*scale),Math.round(height*scale),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap); c.scale(scale,scale);
        boolean dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int background=Color.parseColor(dark?"#141A2B":"#F4F6FC"), ink=Color.parseColor(dark?"#EEF2FC":"#172137"),
            muted=Color.parseColor(dark?"#ABB8D1":"#52617A"), grid=Color.parseColor(dark?"#303B53":"#DCE2EE"),
            green=Color.parseColor(dark?"#67D9B7":"#08785F");
        // TVL chart is temporarily hidden; keep its drawing code for restoration.
        // int blue=Color.parseColor(dark?"#8BAFFF":"#285DE0");
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG); p.setColor(background);
        p.setTypeface(WidgetFont.regular(context));p.setFontFeatureSettings("tnum");
        c.drawRoundRect(new RectF(0,0,width,height),24,24,p);
        long now=System.currentTimeMillis();
        Double tvl=data==null?null:data.currentTvl(now), apy=data==null?null:data.currentApy(now);
        // The native RemoteViews header owns the Roboto counters and their animations.
        String state=data==null?"":tvl==null?"TVL: нет данных DSF API":apy==null?"APY: нет данных DeFiLlama":!WidgetData.fresh(data.tvlAsOf,now)||!WidgetData.fresh(data.apyAsOf,now,WidgetData.APY_MAX_AGE)?"Последние доступные данные":"";
        float top=56,bottom=height-(state.isEmpty()?40:57),left=28,right=width-42;
        if(bottom<top+25) bottom=top+25;
        // Domain tvlDomain=domain(data,false);
        Domain apyDomain=domain(data,true);
        for(int i=0;i<=3;i++) {
            float y=bottom-(bottom-top)*i/3;
            p.setColor(grid);p.setStrokeWidth(.7f);c.drawLine(left,y,right,y,p);
            // text(c,p,compact(tvlDomain.lo+(tvlDomain.hi-tvlDomain.lo)*i/3),left-6,y+3.5f,10.5f,muted,Paint.Align.RIGHT,false);
            text(c,p,axisPercent(apyDomain.lo+(apyDomain.hi-apyDomain.lo)*i/3),right+6,y+3.5f,10.5f,muted,Paint.Align.LEFT,false);
        }
        Drawable logo=context.getDrawable(R.drawable.dsf_logo);
        if(logo!=null) {
            logo=logo.mutate();
            logo.setTint(Color.WHITE);
            int logoWidth=(int)Math.min((right-left)*.8f,240), logoHeight=logoWidth*40/146;
            int cx=Math.round((left+right)/2),cy=Math.round((top+bottom)/2);
            logo.setBounds(cx-logoWidth/2,cy-logoHeight/2,cx+logoWidth/2,cy+logoHeight/2);
            logo.setAlpha(dark?45:30);logo.draw(c);
        }
        long rangeEnd=data==null?now/3600000*3600000:data.end;
        long rangeStart=data==null?rangeEnd-30L*86400000:data.start;
        SimpleDateFormat dateFormat=new SimpleDateFormat("dd.MM",Locale.ROOT);dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        int interval=width>=380?5:10;
        for(int i=0;i<=30;i++) {
            float x=left+(right-left)*i/30;
            boolean major=i%interval==0;
            p.setColor(grid);p.setStrokeWidth(1);c.drawLine(x,bottom,x,bottom+(major?5:2.5f),p);
            if(major)text(c,p,dateFormat.format(new Date(rangeStart+i*86400000L)),x,bottom+18,10.5f,muted,Paint.Align.CENTER,false);
        }
        if(data!=null) {
            c.save(); c.clipRect(left-3,top-3,right+3,bottom+3);
            // drawSeries(c,p,data,false,tvlDomain,left,right,top,bottom,blue);
            drawSeries(c,p,data,true,apyDomain,left,right,top,bottom,green);
            c.restore();
        }
        if(!state.isEmpty())text(c,p,state,width/2f,height-24,10.5f,muted,Paint.Align.CENTER,false);
        text(c,p,"v"+BuildConfig.VERSION_NAME,width-16,height-9,9,muted,Paint.Align.RIGHT,false);
        return bitmap;
    }

    private static void text(Canvas c,Paint p,String s,float x,float y,float size,int color,Paint.Align align,boolean medium) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);
        c.drawText(s,x,y,p);
    }
    record Domain(double lo,double hi) {}
    private static Domain domain(WidgetData data,boolean apy) {
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        if(data!=null)for(WidgetData.Point point:apy?data.apyPoints:data.tvlPoints) {
            Double value=point.value();
            if(value!=null){low=Math.min(low,value);high=Math.max(high,value);}
        }
        if(!Double.isFinite(low))return new Domain(0,apy?10:100000);
        double pad=Math.max((high-low)*.12,Math.max(Math.abs(high)*.015,apy?.1:1));
        return new Domain(apy?low-pad:Math.max(0,low-pad),high+pad);
    }
    private static String compact(double value) {
        double abs=Math.abs(value);String suffix=abs>=1e6?"m":abs>=1e3?"k":"";
        double v=value/(abs>=1e6?1e6:abs>=1e3?1e3:1);
        return "$"+new DecimalFormat(Math.abs(v)>=100?"0":"0.#",DecimalFormatSymbols.getInstance(Locale.US)).format(v)+suffix;
    }
    private static String axisPercent(double value) {return new DecimalFormat("0.#",DecimalFormatSymbols.getInstance(Locale.US)).format(value)+"%";}

    private static void drawSeries(Canvas c,Paint p,WidgetData data,boolean apy,Domain domain,float left,float right,float top,float bottom,int color) {
        List<float[]> segment=new ArrayList<>();
        for(WidgetData.Point point:apy?data.apyPoints:data.tvlPoints) {
            Double value=point.value();
            if(value==null) {drawSegment(c,p,segment,color);segment.clear();continue;}
            float x=left+(float)((double)(point.time()-data.start)/(data.end-data.start))*(right-left);
            float y=bottom-(float)((value-domain.lo)/(domain.hi-domain.lo))*(bottom-top);
            segment.add(new float[]{x,y});
        }
        drawSegment(c,p,segment,color);
    }

    private static void drawSegment(Canvas c,Paint p,List<float[]> points,int color) {
        if(points.isEmpty())return;
        p.setColor(color);p.setStrokeWidth(2.2f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        if(points.size()>1) {
            int n=points.size();float[] slopes=new float[n-1],tangents=new float[n];
            for(int i=0;i<n-1;i++)slopes[i]=(points.get(i+1)[1]-points.get(i)[1])/(points.get(i+1)[0]-points.get(i)[0]);
            tangents[0]=slopes[0];tangents[n-1]=slopes[n-2];
            for(int i=1;i<n-1;i++)tangents[i]=slopes[i-1]*slopes[i]<=0?0:2*slopes[i-1]*slopes[i]/(slopes[i-1]+slopes[i]);
            Path path=new Path();path.moveTo(points.get(0)[0],points.get(0)[1]);
            for(int i=0;i<n-1;i++) {
                float[] a=points.get(i),b=points.get(i+1);float third=(b[0]-a[0])/3;
                path.cubicTo(a[0]+third,a[1]+tangents[i]*third,b[0]-third,b[1]-tangents[i+1]*third,b[0],b[1]);
            }
            c.drawPath(path,p);
        }
        p.setStyle(Paint.Style.FILL);float[] last=points.get(points.size()-1);c.drawCircle(last[0],last[1],2.6f,p);
    }
}
