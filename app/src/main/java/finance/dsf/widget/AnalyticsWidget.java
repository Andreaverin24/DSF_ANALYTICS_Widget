package finance.dsf.widget;

import android.app.PendingIntent;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

public class AnalyticsWidget extends AppWidgetProvider {
    static int[] ids(Context context) { return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context,AnalyticsWidget.class)); }
    @Override public void onReceive(Context context,Intent intent) {
        super.onReceive(context,intent);
        if((Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
            && ids(context).length>0) {
            updateAll(context);
            RefreshJob.schedule(context,true);
        }
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        updateAll(context); RefreshJob.schedule(context,true);
    }
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options) { update(context,manager,id); }
    @Override public void onDisabled(Context context) { RefreshJob.cancel(context); }
    @Override public void onDeleted(Context context,int[] ids) {
        SharedPreferences.Editor edit=context.getSharedPreferences("widget_display",Context.MODE_PRIVATE).edit();
        for(int id:ids)edit.remove(id+":tvl").remove(id+":apy");
        edit.apply();
    }
    @Override public void onEnabled(Context context) { RefreshJob.schedule(context,true); }
    static void updateAll(Context context) {
        updateAll(context,false);
    }
    static void updateAll(Context context,boolean animate) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        for(int id:ids(context))update(context,manager,id,animate);
    }
    static void update(Context context,AppWidgetManager manager,int id) {
        update(context,manager,id,false);
    }
    private static void update(Context context,AppWidgetManager manager,int id,boolean animate) {
        Bundle options=manager.getAppWidgetOptions(id);
        int width=Math.max(220,Math.min(600,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)));
        int height=Math.max(145,Math.min(500,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,220)));
        WidgetData data=SnapshotStore.read(context);
        SharedPreferences displayed=context.getSharedPreferences("widget_display",Context.MODE_PRIVATE);
        String tvl=ChartRenderer.money(data==null?null:data.tvl),apy=ChartRenderer.percent(data==null?null:data.apy);
        RemoteViews views=createViews(context,data,width,height,displayed.getString(id+":tvl",null),
            displayed.getString(id+":apy",null),animate && ValueAnimator.areAnimatorsEnabled());
        manager.updateAppWidget(id,views);
        displayed.edit().putString(id+":tvl",tvl).putString(id+":apy",apy).apply();
    }
    static RemoteViews createViews(Context context,WidgetData data,int width,int height,
        String previousTvl,String previousApy,boolean animate) {
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget);
        views.setImageViewBitmap(R.id.widget_image,ChartRenderer.render(context,data,width,height));
        String tvl=ChartRenderer.money(data==null?null:data.tvl),apy=ChartRenderer.percent(data==null?null:data.apy);
        CounterViews.bind(context,views,width,tvl,apy,previousTvl,previousApy,animate);
        views.setContentDescription(R.id.widget_root,"TVL "+tvl+", APY "+apy+". DSF Analytics, версия "+BuildConfig.VERSION_NAME+". Открыть приложение");
        Intent intent=new Intent(context,MainActivity.class);
        views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
}
