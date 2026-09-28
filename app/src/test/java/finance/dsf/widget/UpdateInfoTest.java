package finance.dsf.widget;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateInfoTest {
    private static final String HASH="a".repeat(64);
    @Test public void versionCodeAndAndroidCompatibilityControlTheOffer() {
        UpdateInfo info=UpdateInfo.validated(13,"0.4.0",29,"https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk",HASH);
        assertTrue(info.available(12,29));
        assertFalse(info.available(13,29));
        assertFalse(info.available(14,29));
        assertFalse(info.available(12,28));
    }
    @Test public void dailyLimitIncludesExactBoundaryAndClockRollback() {
        long start=1800000000000L;
        assertTrue(UpdateInfo.due(start,0));
        assertFalse(UpdateInfo.due(start,start));
        assertFalse(UpdateInfo.due(start+UpdateInfo.CHECK_INTERVAL-1,start));
        assertTrue(UpdateInfo.due(start+UpdateInfo.CHECK_INTERVAL,start));
        assertTrue(UpdateInfo.due(start-1,start));
    }
    @Test public void cachedOfferExpiresAfterSevenDaysAndRejectsFutureTimestamps() {
        long start=1800000000000L;
        assertTrue(UpdateInfo.fresh(start,start));
        assertTrue(UpdateInfo.fresh(start+UpdateInfo.CACHE_LIFETIME-1,start));
        assertFalse(UpdateInfo.fresh(start+UpdateInfo.CACHE_LIFETIME,start));
        assertFalse(UpdateInfo.fresh(start-1,start));
        assertFalse(UpdateInfo.fresh(start,0));
    }
    @Test public void downloadCannotEscapeTheVersionedDsfPath() {
        for(String url:new String[]{"http://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk",
            "https://evil.test/downloads/DSF-Finance-Widget-0.4.0.apk",
            "https://dsf.finance.evil.test/downloads/DSF-Finance-Widget-0.4.0.apk",
            "https://dsf.finance@evil.test/downloads/DSF-Finance-Widget-0.4.0.apk",
            "https://dsf.finance/downloads/DSF-Finance-Widget-0.3.9.apk",
            "https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk?redirect=evil",
            "intent://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk"}) {
            assertThrows(IllegalArgumentException.class,()->UpdateInfo.validated(13,"0.4.0",26,url,HASH));
        }
    }
    @Test public void malformedVersionAndHashAreRejected() {
        for(String version:new String[]{"", "../0.4.0", "0.4.0\n", "<b>1.0</b>", "1.0?"})
            assertThrows(IllegalArgumentException.class,()->UpdateInfo.validated(13,version,26,"https://dsf.finance/downloads/DSF-Finance-Widget-"+version+".apk",HASH));
        assertThrows(IllegalArgumentException.class,()->UpdateInfo.validated(0,"0.4.0",26,"https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk",HASH));
        assertThrows(IllegalArgumentException.class,()->UpdateInfo.validated(13,"0.4.0",26,"https://dsf.finance/downloads/DSF-Finance-Widget-0.4.0.apk","x".repeat(64)));
    }
}
