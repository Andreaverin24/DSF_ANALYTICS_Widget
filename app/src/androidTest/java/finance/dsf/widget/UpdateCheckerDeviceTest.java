package finance.dsf.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateCheckerDeviceTest {
    static String manifest(int code,int sdk) {
        return "{\"schemaVersion\":1,\"applicationId\":\"finance.dsf.widget\",\"versionCode\":"+code+
            ",\"versionName\":\"0.4.0\",\"minSdk\":"+sdk+",\"apkUrl\":\"https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk\",\"sha256\":\""+copies('a',64)+"\"}";
    }
    private static String copies(char c,int count){char[] chars=new char[count];java.util.Arrays.fill(chars,c);return new String(chars);}
    private Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void parserRejectsWrongJsonTypesAndIdentity() throws Exception {
        String valid=manifest(13,26);
        assertEquals(13,UpdateInfo.parse(valid).versionCode());
        for(String raw:new String[]{"{}", "null", valid+"garbage",valid.replace("\"versionCode\":13","\"versionCode\":13,\"versionCode\":14"),
            valid.replace("\"versionCode\":13","\"versionCode\":\"13\""),
            valid.replace("\"versionCode\":13","\"versionCode\":13.5"),valid.replace("\"versionCode\":13","\"versionCode\":true"),
            valid.replace("finance.dsf.widget","finance.dsf.other"),valid.replace("schemaVersion\":1","schemaVersion\":2"),
            valid.replace("minSdk\":26","minSdk\":null"), valid.replace("dsf.finance/downloads","evil.test/downloads")}) {
            assertThrows(Exception.class,()->UpdateInfo.parse(raw));
        }
    }
    @Test public void connectionEnforcesStatusRedirectAndSizeLimits() throws Exception {
        FakeConnection valid=new FakeConnection(200,manifest(13,26));
        assertEquals(manifest(13,26),UpdateChecker.download(valid));
        assertFalse(valid.getInstanceFollowRedirects());assertTrue(valid.closed);
        for(int code:new int[]{302,404,500}) {
            FakeConnection bad=new FakeConnection(code,manifest(13,26));
            assertThrows(Exception.class,()->UpdateChecker.download(bad));assertTrue(bad.closed);assertFalse(bad.opened);
        }
        FakeConnection huge=new FakeConnection(200,copies('x',16385));
        assertThrows(Exception.class,()->UpdateChecker.download(huge));assertTrue(huge.closed);
        FakeConnection interrupted=new FakeConnection(200,"") {
            @Override public InputStream getInputStream() throws IOException {throw new IOException("offline");}
        };
        assertThrows(Exception.class,()->UpdateChecker.download(interrupted));assertTrue(interrupted.closed);
    }
    @Test public void failuresDoNotExtendOldOffersAndValidResponsesReplaceThem() throws Exception {
        SharedPreferences prefs=app().getSharedPreferences("update_test_cache",0);
        prefs.edit().clear().commit();long now=System.currentTimeMillis();
        try {
            UpdateChecker.perform(prefs,now,()->new FakeConnection(200,manifest(13,26)));
            assertNotNull(UpdateChecker.cached(prefs,now,12,29));
            UpdateChecker.perform(prefs,now+UpdateInfo.CHECK_INTERVAL,()->{throw new IOException("offline");});
            assertEquals(now,prefs.getLong(UpdateChecker.CHECKED_AT,0));
            assertEquals(now+UpdateInfo.CHECK_INTERVAL,prefs.getLong(UpdateChecker.ATTEMPTED_AT,0));
            assertNotNull(UpdateChecker.cached(prefs,now+UpdateInfo.CHECK_INTERVAL,12,29));
            assertNull(UpdateChecker.cached(prefs,now+UpdateInfo.CACHE_LIFETIME,12,29));
            UpdateChecker.perform(prefs,now+1,()->new FakeConnection(200,manifest(12,26)));
            assertNull(UpdateChecker.cached(prefs,now+1,12,29));
            UpdateChecker.perform(prefs,now+2,()->new FakeConnection(200,manifest(13,99)));
            assertNull(UpdateChecker.cached(prefs,now+2,12,29));
        } finally {prefs.edit().clear().commit();}
    }
    @Test public void deadlineDisconnectsAStalledRead() throws Exception {
        CountDownLatch disconnected=new CountDownLatch(1);
        FakeConnection stalled=new FakeConnection(200,"") {
            @Override public InputStream getInputStream() {return new InputStream(){
                @Override public int read() throws IOException {
                    try {disconnected.await(25,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                    throw new IOException("Disconnected");
                }
            };}
            @Override public void disconnect(){super.disconnect();disconnected.countDown();}
        };
        long started=android.os.SystemClock.elapsedRealtime();
        assertThrows(Exception.class,()->UpdateChecker.download(stalled));
        long elapsed=android.os.SystemClock.elapsedRealtime()-started;
        assertTrue("deadline "+elapsed,elapsed>=19000 && elapsed<24000);assertTrue(stalled.closed);
    }
    @Test public void concurrentOpeningsShareOneRequestAndRespectPersistedAttempt() throws Exception {
        SharedPreferences prefs=app().getSharedPreferences("update_test_concurrency",0);
        prefs.edit().clear().commit();long now=System.currentTimeMillis();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),saved=new CountDownLatch(1);
        SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if(UpdateChecker.MANIFEST.equals(k))saved.countDown();};
        prefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            assertTrue(UpdateChecker.checkIfDue(prefs,now,()->{started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return new FakeConnection(200,manifest(13,26));}));
            assertTrue(started.await(3,TimeUnit.SECONDS));
            assertFalse(UpdateChecker.checkIfDue(prefs,now,()->{throw new AssertionError("Duplicate request");}));
            release.countDown();assertTrue(saved.await(5,TimeUnit.SECONDS));
            assertFalse(UpdateChecker.checkIfDue(prefs,now+1000,()->{throw new AssertionError("Daily limit");}));
            assertNotNull(UpdateChecker.cached(prefs,now+1000,12,29));
        } finally {release.countDown();prefs.unregisterOnSharedPreferenceChangeListener(listener);prefs.edit().clear().commit();}
    }
    @Test public void screenShowsOnlyNewerVersionsAndWidgetAnnouncesInstalledVersion() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        SharedPreferences prefs=UpdateChecker.preferences(app());Map<String,?> before=prefs.getAll();
        Activity activity=null;long now=System.currentTimeMillis();
        try {
            prefs.edit().putLong(UpdateChecker.ATTEMPTED_AT,now).putLong(UpdateChecker.CHECKED_AT,now)
                .putString(UpdateChecker.MANIFEST,manifest(BuildConfig.VERSION_CODE+1,26)).commit();
            activity=instrumentation.startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();Activity screen=activity;
            instrumentation.runOnMainSync(()->{
                assertEquals(View.VISIBLE,screen.findViewById(R.id.update_panel).getVisibility());
                assertEquals("v"+BuildConfig.VERSION_NAME,((TextView)screen.findViewById(R.id.app_version)).getText().toString());
                Instrumentation.ActivityMonitor browser=new Instrumentation.ActivityMonitor(){
                    @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                        assertEquals(Intent.ACTION_VIEW,intent.getAction());
                        assertEquals("https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk",intent.getDataString());
                        return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                    }
                };
                instrumentation.addMonitor(browser);
                try {assertTrue(screen.findViewById(R.id.download_update).performClick());assertEquals(1,browser.getHits());}
                finally {instrumentation.removeMonitor(browser);}
                View widget=AnalyticsWidget.createViews(app(),SnapshotStore.read(app()),240,220,null,null,false).apply(app(),new FrameLayout(app()));
                assertTrue(widget.getContentDescription().toString().contains(BuildConfig.VERSION_NAME));
                prefs.edit().putString(UpdateChecker.MANIFEST,manifest(BuildConfig.VERSION_CODE,26)).commit();
                assertEquals(View.GONE,screen.findViewById(R.id.update_panel).getVisibility());
            });
            instrumentation.runOnMainSync(activity::finish);activity=null;
            prefs.edit().putString(UpdateChecker.MANIFEST,manifest(BuildConfig.VERSION_CODE+1,26)).commit();
            activity=instrumentation.startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();Activity reopened=activity;
            instrumentation.runOnMainSync(()->assertEquals(View.VISIBLE,reopened.findViewById(R.id.update_panel).getVisibility()));
        } finally {
            if(activity!=null)instrumentation.runOnMainSync(activity::finish);
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> e:before.entrySet()) {
                if(e.getValue() instanceof Long)restore.putLong(e.getKey(),(Long)e.getValue());
                else if(e.getValue() instanceof String)restore.putString(e.getKey(),(String)e.getValue());
            }
            restore.commit();
        }
    }
    private static class FakeConnection extends HttpsURLConnection {
        final int status;final byte[] body;boolean closed,opened;
        FakeConnection(int status,String text) throws Exception {super(new URL(UpdateChecker.MANIFEST_URL));this.status=status;body=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        @Override public int getResponseCode(){return status;}
        @Override public long getContentLengthLong(){return -1;}
        @Override public InputStream getInputStream() throws IOException {opened=true;return new ByteArrayInputStream(body);}
        @Override public void disconnect(){closed=true;}
        @Override public void connect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public String getCipherSuite(){return "TEST";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return new Certificate[0];}
    }
}
