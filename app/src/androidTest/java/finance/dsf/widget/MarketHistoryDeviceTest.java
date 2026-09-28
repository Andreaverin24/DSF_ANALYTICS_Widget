package finance.dsf.widget;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.time.Instant;

public class MarketHistoryDeviceTest {
    private static final long NOW=Instant.parse("2026-09-27T10:45:00Z").toEpochMilli();
    private String asset(String name) throws Exception {
        Context test=InstrumentationRegistry.getInstrumentation().getContext();
        try(InputStream in=test.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return out.toString("UTF-8");
        }
    }
    private WidgetData live() throws Exception {
        return WidgetData.parse(MarketHistory.snapshot(asset("apy-tvl.json"),asset("llama-history.json"),null,NOW).json());
    }
    @Test public void exactSourceValuesAndEveryPointArePreserved() throws Exception {
        WidgetData actual=live();
        assertEquals(508494.06,actual.tvl,0.000001);
        assertEquals(NOW,actual.tvlAsOf);
        assertEquals(9.6939,actual.apy,0.000000001);
        JSONArray input=new JSONObject(asset("llama-history.json")).getJSONArray("data");
        int count=0;
        for(int i=0;i<input.length();i++) {
            JSONObject row=input.getJSONObject(i);long time=Instant.parse(row.getString("timestamp")).toEpochMilli();
            if(time<actual.start || time>actual.end)continue;
            assertEquals(time,actual.apyPoints.get(count).time());
            assertEquals(WidgetData.value(row,"apy"),actual.apyPoints.get(count).value());count++;
        }
        assertEquals(count,actual.apyPoints.size());assertTrue(count>=30);
        assertTrue(actual.tvlPoints.isEmpty());
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(int width:new int[]{240,320,400}) {
            Bitmap bitmap=ChartRenderer.render(app,actual,width,250);
            try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),"widget-"+width+".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            bitmap.recycle();
        }
    }
    @Test public void zeroAndNegativeApyRemainVisible() throws Exception {
        String apy="{\"status\":\"success\",\"data\":[{\"timestamp\":\"2026-09-26T10:00:00Z\",\"apy\":0,\"tvlUsd\":1},{\"timestamp\":\"2026-09-27T10:00:00Z\",\"apy\":-7,\"tvlUsd\":2}]}";
        WidgetData data=WidgetData.parse(MarketHistory.snapshot(asset("apy-tvl.json"),apy,null,NOW).json());
        assertEquals(0,data.apyPoints.get(0).value(),0);assertEquals(-7,data.apy,0);
        assertEquals(508494.06,data.tvl,0.000001);
    }
    @Test public void eachSourceCanUpdateWhileTheOtherIsOffline() throws Exception {
        WidgetData prior=live();
        String apy="{\"status\":\"success\",\"data\":[{\"timestamp\":\"2026-09-27T10:00:00Z\",\"apy\":11}]}";
        WidgetData next=WidgetData.parse(MarketHistory.snapshot(null,apy,prior,NOW+60000).json());
        assertEquals(prior.tvl,next.tvl);assertEquals(11,next.apy,0);
        assertEquals(prior.tvlAsOf,next.tvlAsOf);
        MarketHistory.Result failed=MarketHistory.snapshot("{\"tvl\":600000}","{}",prior,NOW+60000);
        WidgetData updated=WidgetData.parse(failed.json());
        assertFalse(failed.errors().isEmpty());assertEquals(prior.apy,updated.apy);
        assertEquals(prior.apyAsOf,updated.apyAsOf);assertEquals(prior.apyPoints,updated.apyPoints);
        assertEquals(600000,updated.tvl,0);assertEquals(NOW+60000,updated.tvlAsOf);
    }
    @Test public void futurePointsAreNotPlotted() throws Exception {
        JSONObject source=new JSONObject(asset("llama-history.json"));
        source.getJSONArray("data").put(new JSONObject().put("timestamp","2099-01-01T00:00:00Z").put("apy",999));
        WidgetData data=WidgetData.parse(MarketHistory.snapshot(asset("apy-tvl.json"),source.toString(),null,NOW).json());
        assertEquals(9.6939,data.apy,0.00000001);
    }
    @Test public void staleCacheKeepsValuesAndOldApySourceCannotBeRead() throws Exception {
        WidgetData data=live();assertEquals(data.apy,data.currentApy(NOW+7*86400000L));
        assertEquals(data.tvl,data.currentTvl(NOW+7*86400000L));
        assertFalse(WidgetData.fresh(data.apyAsOf,NOW+7*86400000L,WidgetData.APY_MAX_AGE));
        try {WidgetData.parse("{\"schemaVersion\":2,\"source\":\"dsf-actual-apy-internal\"}");fail("old source accepted");}
        catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().contains("источник"));}
    }
    @Test public void onlyTopLevelTvlIsUsedAndZeroIsAValue() throws Exception {
        JSONObject response=new JSONObject(asset("apy-tvl.json")).put("tvl",0)
            .put("apyToday",999).put("apy365",888).put("placed",777)
            .put("data",new JSONObject().put("tvl",666));
        WidgetData data=WidgetData.parse(MarketHistory.snapshot(response.toString(),null,live(),NOW+60000).json());
        assertEquals(0,data.tvl,0);assertEquals(NOW+60000,data.tvlAsOf);
        assertEquals(9.6939,data.apy,0.000000001);
        assertTrue(data.tvlPoints.isEmpty());
    }
    @Test public void invalidTvlKeepsPreviousValueOrRemainsMissing() throws Exception {
        WidgetData prior=live();
        for(String invalid:new String[]{"{}","{\"tvl\":null}","{\"tvl\":\"508494.06\"}",
            "{\"tvl\":-1}","{\"tvl\":true}","{\"tvl\":1e400}","not json","{\"data\":{\"tvl\":42}}"}) {
            MarketHistory.Result failed=MarketHistory.snapshot(invalid,null,prior,NOW+60000);
            assertFalse(invalid,failed.errors().isEmpty());
            WidgetData cached=WidgetData.parse(failed.json());
            assertEquals(invalid,prior.tvl,cached.tvl);assertEquals(prior.tvlAsOf,cached.tvlAsOf);
            WidgetData empty=WidgetData.parse(MarketHistory.snapshot(invalid,null,null,NOW).json());
            assertNull(invalid,empty.tvl);assertEquals(0,empty.tvlAsOf);
        }
    }
    @Test public void upgradePreservesLlamaButNeverReusesHistoryTvl() throws Exception {
        JSONObject legacy=new JSONObject(MarketHistory.snapshot(asset("apy-tvl.json"),asset("llama-history.json"),null,NOW).json())
            .put("schemaVersion",3).put("source","tvl:dsf-core;apy:defillama:774082cb-0775-4718-bac0-fde0165c8f9c")
            .put("tvlPoints",new JSONArray().put(new JSONObject().put("time",NOW).put("value",12345)));
        legacy.getJSONObject("current").put("tvl",12345);
        WidgetData migrated=WidgetData.parse(legacy.toString());
        assertNull(migrated.tvl);assertEquals(0,migrated.tvlAsOf);assertTrue(migrated.tvlPoints.isEmpty());
        assertEquals(live().apyPoints,migrated.apyPoints);assertEquals(live().apy,migrated.apy);
        assertEquals(live().averageApy30d(),migrated.averageApy30d());
        WidgetData offline=WidgetData.parse(MarketHistory.snapshot(null,null,migrated,NOW+60000).json());
        assertNull(offline.tvl);assertEquals(migrated.apyPoints,offline.apyPoints);
        WidgetData online=WidgetData.parse(MarketHistory.snapshot(asset("apy-tvl.json"),null,migrated,NOW+60000).json());
        assertEquals(508494.06,online.tvl,0.000001);assertEquals(NOW+60000,online.tvlAsOf);
        for(String source:new String[]{WidgetData.SOURCE,"tvl:dsf-core;apy:unknown"}) {
            legacy.put("source",source);
            try {WidgetData.parse(legacy.toString());fail("unexpected legacy source accepted");}
            catch(IllegalArgumentException expected) {assertTrue(expected.getMessage().contains("источник"));}
        }
    }
}
