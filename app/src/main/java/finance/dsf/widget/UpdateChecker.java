package finance.dsf.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

final class UpdateChecker {
    static final String MANIFEST_URL="https://dsf.finance/downloads/android/latest.json";
    static final String PREFERENCES="app_updates", MANIFEST="manifest", CHECKED_AT="checked_at", ATTEMPTED_AT="attempted_at";
    private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor();
    private static boolean running;
    interface ConnectionFactory {HttpsURLConnection open() throws Exception;}

    static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
    }
    static UpdateInfo cached(SharedPreferences prefs,long now,int installedCode,int deviceSdk) {
        if(!UpdateInfo.fresh(now,prefs.getLong(CHECKED_AT,0)))return null;
        try {
            UpdateInfo info=UpdateInfo.parse(prefs.getString(MANIFEST,""));
            return info.available(installedCode,deviceSdk)?info:null;
        } catch(Exception ignored) {return null;}
    }
    static UpdateInfo cached(Context context) {
        return cached(preferences(context),System.currentTimeMillis(),BuildConfig.VERSION_CODE,Build.VERSION.SDK_INT);
    }
    static void checkIfDue(Context context) {
        checkIfDue(preferences(context),System.currentTimeMillis(),()->(HttpsURLConnection)new URL(MANIFEST_URL).openConnection());
    }
    static synchronized boolean checkIfDue(SharedPreferences prefs,long now,ConnectionFactory factory) {
        if(running || !UpdateInfo.due(now,prefs.getLong(ATTEMPTED_AT,0)))return false;
        running=true;
        WORKER.execute(()->{
            try {perform(prefs,now,factory);}
            finally {synchronized(UpdateChecker.class){running=false;}}
        });
        return true;
    }
    static void perform(SharedPreferences prefs,long now,ConnectionFactory factory) {
        // Persist even failures: repeated Activity opens must not cause repeated network requests.
        if(!prefs.edit().putLong(ATTEMPTED_AT,now).commit())return;
        try {
            String raw=download(factory.open());
            UpdateInfo.parse(raw);
            prefs.edit().putString(MANIFEST,raw).putLong(CHECKED_AT,now).apply();
        } catch(Exception ignored) {
            // Update discovery is optional; never interrupt analytics or extend the old offer's lifetime.
        }
    }
    static String download(HttpsURLConnection connection) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+20000;
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Accept-Encoding","identity");
        connection.setRequestProperty("User-Agent","DSF-Analytics-Widget/"+BuildConfig.VERSION_NAME);
        ScheduledFuture<?> timeout=DEADLINES.schedule(connection::disconnect,20,TimeUnit.SECONDS);
        try {
            if(connection.getResponseCode()!=200)throw new IllegalStateException("Update HTTP error");
            if(connection.getContentLengthLong()>16384)throw new IllegalStateException("Manifest too large");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[2048];
                while(true) {
                    long remaining=deadline-SystemClock.elapsedRealtime();
                    if(remaining<=0 || Thread.currentThread().isInterrupted())throw new IllegalStateException("Update deadline");
                    connection.setReadTimeout((int)Math.min(8000,remaining));
                    int count=input.read(buffer);
                    if(count==-1)break;
                    if(output.size()+count>16384)throw new IllegalStateException("Manifest too large");
                    output.write(buffer,0,count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally {timeout.cancel(false);connection.disconnect();}
    }
}
