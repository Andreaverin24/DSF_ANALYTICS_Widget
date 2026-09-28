package finance.dsf.widget;

import android.content.Context;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ViewFlipper;
import android.widget.RemoteViews;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class CounterViewsDeviceTest {
    @Test public void optionalPhoneDemoUsesHistoryAndRestoresLatestSnapshot() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("visualDemo")));
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] ids=AnalyticsWidget.ids(app);assumeTrue(ids.length>0);
        WidgetData latest=SnapshotStore.read(app),previous=SnapshotStore.read(app);
        assertNotNull(latest);assertNotNull(previous);
        for(int i=latest.tvlPoints.size()-1;i>=0;i--) {
            Double candidate=latest.tvlPoints.get(i).value();
            if(candidate!=null && !ChartRenderer.money(candidate).equals(ChartRenderer.money(latest.tvl))) {previous.tvl=candidate;break;}
        }
        for(int i=latest.apyPoints.size()-1;i>=0;i--) {
            Double candidate=latest.apyPoints.get(i).value();
            if(candidate!=null && !ChartRenderer.percent(candidate).equals(ChartRenderer.percent(latest.apy))) {previous.apy=candidate;break;}
        }
        AppWidgetManager manager=AppWidgetManager.getInstance(app);
        try {
            for(int id:ids)publish(app,manager,id,previous,null,null,false);
            Thread.sleep(2500);
            capture(app,"counter-before-0.3.6.png");
            for(int id:ids)publish(app,manager,id,latest,ChartRenderer.money(previous.tvl),ChartRenderer.percent(previous.apy),true);
            Thread.sleep(160);
            capture(app,"counter-during-0.3.6.png");
            Thread.sleep(120);
            capture(app,"counter-during2-0.3.6.png");
            Thread.sleep(1000);
            capture(app,"counter-after-0.3.6.png");
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->AnalyticsWidget.updateAll(app));
        }
    }
    private static void capture(Context app,String name) throws Exception {
        Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screen);
        try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),name))) {screen.compress(Bitmap.CompressFormat.PNG,100,out);}
        screen.recycle();
    }
    private static void publish(Context app,AppWidgetManager manager,int id,WidgetData data,String oldTvl,String oldApy,boolean animate) {
        Bundle options=manager.getAppWidgetOptions(id);
        int width=Math.max(220,Math.min(600,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)));
        int height=Math.max(145,Math.min(500,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,220)));
        manager.updateAppWidget(id,AnalyticsWidget.createViews(app,data,width,height,oldTvl,oldApy,animate));
    }
    @Test public void remoteViewsApplyAndReapplyKeepTheFinalDigits() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            WidgetData data=new WidgetData();data.tvl=1000d;data.apy=-2d;
            data.end=System.currentTimeMillis();data.start=data.end-30L*86400000;
            RemoteViews remote=AnalyticsWidget.createViews(app,data,240,220,"$999","-1,00%",true);
            View root=remote.apply(app,new FrameLayout(app));
            assertEquals("$1\u00a0000",visible(root.findViewById(R.id.tvl_digits)));
            assertEquals("-2,00%",visible(root.findViewById(R.id.apy_digits)));
            assertTrue(flippers(root)>0);
            remote.reapply(app,root);
            assertEquals("$1\u00a0000",visible(root.findViewById(R.id.tvl_digits)));
            assertEquals("-2,00%",visible(root.findViewById(R.id.apy_digits)));
            for(int width:new int[]{220,240,320,400,600}) {
                View sized=AnalyticsWidget.createViews(app,data,width,220,null,null,false).apply(app,new FrameLayout(app));
                int px=Math.round(width*app.getResources().getDisplayMetrics().density);
                sized.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Math.round(220*app.getResources().getDisplayMetrics().density),View.MeasureSpec.EXACTLY));
                sized.layout(0,0,sized.getMeasuredWidth(),sized.getMeasuredHeight());
                View left=sized.findViewById(R.id.tvl_digits),right=sized.findViewById(R.id.apy_label);
                assertTrue("Header overlap at "+width,left.getRight()<=right.getLeft());
                assertEquals(0,flippers(sized));
                ViewGroup digits=(ViewGroup)left;
                for(int i=0;i<digits.getChildCount();i++) {
                    ImageView digit=(ImageView)digits.getChildAt(i);
                    assertTrue("Digit needs width",digit.getWidth()>0);
                    assertTrue("Digit needs height",digit.getHeight()>0);
                    assertNotNull(digit.getDrawable());
                }
            }
        });
    }
    private static String visible(View view) {
        if(view instanceof ViewFlipper flipper) {
            assertEquals(1,flipper.getDisplayedChild());
            assertFalse(flipper.isFlipping());
            assertEquals(600,flipper.getInAnimation().getDuration());
            return visible(flipper.getCurrentView());
        }
        if(view instanceof TextView text)return text.getText().toString();
        if(view instanceof ImageView image)return image.getContentDescription()==null?"":image.getContentDescription().toString();
        StringBuilder result=new StringBuilder();
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.append(visible(group.getChildAt(i)));
        return result.toString();
    }
    private static int flippers(View view) {
        if(view instanceof ViewFlipper)return 1;
        int count=0;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=flippers(group.getChildAt(i));
        return count;
    }
}
