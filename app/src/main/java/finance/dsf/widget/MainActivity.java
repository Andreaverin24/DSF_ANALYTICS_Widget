package finance.dsf.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String HOME_PAGE="https://dsf.finance/";
    private static final int BLUE=Color.rgb(57,86,254);
    private TextView averageApy,currentApy,currentTvl;
    private TextView updateLabel;
    private LinearLayout updatePanel;
    private final Runnable expireUpdate=this::showUpdate;
    private final SharedPreferences.OnSharedPreferenceChangeListener updateListener=(prefs,key)->showUpdate();
    private final SharedPreferences.OnSharedPreferenceChangeListener snapshotListener=(prefs,key)->{
        if(SnapshotStore.UPDATED.equals(key))showStatistics();
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244,246,252));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24),dp(32),dp(24),dp(32));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            content.setPadding(dp(24),dp(32)+insets.getSystemWindowInsetTop(),dp(24),dp(32)+insets.getSystemWindowInsetBottom());
            return insets;
        });

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.dsf_logo);
        logo.setContentDescription("DSF.Finance");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams=new LinearLayout.LayoutParams(dp(146),dp(40));
        logoParams.bottomMargin=dp(28);
        content.addView(logo,logoParams);

        int availableWidth=getResources().getDisplayMetrics().widthPixels-dp(48);
        int panelWidth=Math.min(availableWidth,dp(440));
        LinearLayout statistics=new LinearLayout(this);
        statistics.setOrientation(LinearLayout.VERTICAL);
        statistics.setPadding(dp(18),dp(4),dp(18),dp(4));
        GradientDrawable card=new GradientDrawable();
        card.setColor(Color.WHITE);card.setCornerRadius(dp(20));
        statistics.setBackground(card);
        LinearLayout.LayoutParams statisticsParams=new LinearLayout.LayoutParams(panelWidth,-2);
        statisticsParams.bottomMargin=dp(24);
        content.addView(statistics,statisticsParams);
        averageApy=statistic(statistics,R.string.average_apy_30d,R.id.stats_average_apy);
        divider(statistics);
        currentApy=statistic(statistics,R.string.current_apy,R.id.stats_current_apy);
        divider(statistics);
        currentTvl=statistic(statistics,R.string.metric_tvl,R.id.stats_tvl);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        content.addView(actions,new LinearLayout.LayoutParams(panelWidth,-2));
        button(actions,R.string.add_widget,true,view->{
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            if(manager.isRequestPinAppWidgetSupported())
                manager.requestPinAppWidget(new ComponentName(this,AnalyticsWidget.class),null,null);
            else Toast.makeText(this,R.string.add_widget_manually,Toast.LENGTH_LONG).show();
        });
        button(actions,R.string.open_dsf_site,false,view->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(HOME_PAGE))));

        updatePanel=new LinearLayout(this);
        updatePanel.setId(R.id.update_panel);
        updatePanel.setOrientation(LinearLayout.VERTICAL);
        updatePanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams updateParams=new LinearLayout.LayoutParams(panelWidth,-2);
        updateParams.topMargin=dp(24);
        content.addView(updatePanel,updateParams);
        updateLabel=new TextView(this);
        updateLabel.setGravity(Gravity.CENTER);updateLabel.setTextColor(Color.rgb(82,97,122));
        updateLabel.setTextSize(14);updateLabel.setTypeface(WidgetFont.regular(this));
        updatePanel.addView(updateLabel,new LinearLayout.LayoutParams(-1,-2));
        Button download=button(updatePanel,R.string.download_update,false,view->{
            UpdateInfo available=UpdateChecker.cached(this);
            if(available==null){showUpdate();return;}
            try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(available.apkUrl())));}
            catch(ActivityNotFoundException ignored){Toast.makeText(this,R.string.browser_unavailable,Toast.LENGTH_SHORT).show();}
        });
        download.setId(R.id.download_update);
        TextView version=new TextView(this);
        version.setId(R.id.app_version);version.setText("v"+BuildConfig.VERSION_NAME);
        version.setTextSize(12);version.setTypeface(WidgetFont.regular(this));
        version.setTextColor(Color.rgb(82,97,122));version.setPadding(0,dp(24),0,0);
        content.addView(version);
        setContentView(scroll);
    }

    private TextView statistic(LinearLayout parent,int label,int valueId) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(16),0,dp(16));
        TextView name=new TextView(this);
        name.setText(label);name.setTypeface(WidgetFont.regular(this));
        name.setTextColor(Color.rgb(82,97,122));name.setMaxLines(1);
        name.setAutoSizeTextTypeUniformWithConfiguration(12,14,1,TypedValue.COMPLEX_UNIT_SP);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=new TextView(this);
        value.setId(valueId);value.setText("—");value.setTextSize(23);
        value.setTypeface(WidgetFont.regular(this));value.setFontFeatureSettings("tnum");
        value.setTextColor(Color.rgb(23,33,55));value.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(-2,-2);
        valueParams.setMarginStart(dp(12));row.addView(value,valueParams);
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        return value;
    }
    private void divider(LinearLayout parent) {
        View line=new View(this);line.setBackgroundColor(Color.rgb(232,237,246));
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void showStatistics() {
        WidgetData data=SnapshotStore.read(this);
        averageApy.setText(ChartRenderer.percent(data==null?null:data.averageApy30d()));
        currentApy.setText(ChartRenderer.percent(data==null?null:data.apy));
        currentTvl.setText(ChartRenderer.money(data==null?null:data.tvl));
    }

    private void showUpdate() {
        updatePanel.removeCallbacks(expireUpdate);
        UpdateInfo available=UpdateChecker.cached(this);
        updatePanel.setVisibility(available==null?View.GONE:View.VISIBLE);
        if(available!=null) {
            updateLabel.setText(getString(R.string.update_available,available.versionName()));
            long checkedAt=UpdateChecker.preferences(this).getLong(UpdateChecker.CHECKED_AT,0);
            updatePanel.postDelayed(expireUpdate,Math.max(1,checkedAt+UpdateInfo.CACHE_LIFETIME-System.currentTimeMillis()));
        }
    }
    private Button button(LinearLayout parent,int label,boolean primary,View.OnClickListener listener) {
        Button button=new Button(this);
        button.setText(label);button.setAllCaps(false);button.setTextSize(16);
        button.setTypeface(Typeface.create(WidgetFont.regular(this),Typeface.BOLD));
        button.setTextColor(primary?Color.WHITE:BLUE);
        button.setGravity(Gravity.CENTER);button.setMinHeight(dp(56));
        button.setPadding(dp(18),dp(16),dp(18),dp(16));
        button.setBackgroundTintList(null);button.setStateListAnimator(null);
        GradientDrawable shape=new GradientDrawable();
        shape.setCornerRadius(dp(18));shape.setColor(primary?BLUE:Color.rgb(231,236,255));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33FFFFFF:0x223956FE),shape,null));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        if(parent.getChildCount()>0)params.topMargin=dp(14);
        parent.addView(button,params);
        return button;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onResume() {
        super.onResume();
        getSharedPreferences("sync",0).registerOnSharedPreferenceChangeListener(snapshotListener);
        showStatistics();
        UpdateChecker.preferences(this).registerOnSharedPreferenceChangeListener(updateListener);
        showUpdate();
        UpdateChecker.checkIfDue(this);
        WidgetData data=SnapshotStore.read(this);
        if(data==null || data.tvl==null || data.apy==null || !SnapshotStore.error(this).isEmpty()
            || System.currentTimeMillis()-data.generatedAt>=30*60000L)
            RefreshJob.schedule(this,true);
    }
    @Override protected void onPause() {
        updatePanel.removeCallbacks(expireUpdate);
        getSharedPreferences("sync",0).unregisterOnSharedPreferenceChangeListener(snapshotListener);
        UpdateChecker.preferences(this).unregisterOnSharedPreferenceChangeListener(updateListener);
        super.onPause();
    }
}
