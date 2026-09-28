package finance.dsf.widget;

import org.junit.Test;
import static org.junit.Assert.*;

public class AverageApyTest {
    private WidgetData data() {WidgetData data=new WidgetData();data.start=100;data.end=200;return data;}
    @Test public void averagesOnlySourceHistoryIncludingZeroAndNegativeRates() {
        WidgetData data=data();data.apy=999d;data.tvl=500000d;
        data.apyPoints.add(new WidgetData.Point(110,-3d));
        data.apyPoints.add(new WidgetData.Point(120,0d));
        data.apyPoints.add(new WidgetData.Point(130,12d));
        data.apyPoints.add(new WidgetData.Point(140,null));
        assertEquals(3d,data.averageApy30d(),0.000001);
    }
    @Test public void includesWindowBoundariesAndExcludesOlderAndFuturePoints() {
        WidgetData data=data();
        data.apyPoints.add(new WidgetData.Point(99,1000d));
        data.apyPoints.add(new WidgetData.Point(100,6d));
        data.apyPoints.add(new WidgetData.Point(200,12d));
        data.apyPoints.add(new WidgetData.Point(201,1000d));
        assertEquals(9d,data.averageApy30d(),0.000001);
    }
    @Test public void missingHistoryIsNotZeroAndMissingDaysAreNotFabricated() {
        WidgetData data=data();assertNull(data.averageApy30d());
        data.apyPoints.add(new WidgetData.Point(150,null));
        assertNull(data.averageApy30d());
        data.apyPoints.add(new WidgetData.Point(160,8.25d));
        assertEquals(8.25d,data.averageApy30d(),0.000001);
    }
}
