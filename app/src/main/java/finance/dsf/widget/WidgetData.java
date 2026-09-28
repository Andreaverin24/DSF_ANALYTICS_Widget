package finance.dsf.widget;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.MathContext;

public final class WidgetData {
    public static final long MAX_AGE=2*3600000L, APY_MAX_AGE=48*3600000L;
    public static final int SCHEMA_VERSION=4;
    public static final String SOURCE="tvl:dsf-proxy-apy;apy:defillama:774082cb-0775-4718-bac0-fde0165c8f9c";
    private static final String LEGACY_SOURCE="tvl:dsf-core;apy:defillama:774082cb-0775-4718-bac0-fde0165c8f9c";
    // tvlAsOf is the receipt time of /proxy/apy, which provides no source timestamp.
    public long generatedAt,start,end,tvlAsOf,apyAsOf;
    public Double tvl,apy;
    public String apyCaption="APY: DeFiLlama · TVL: DSF API";
    public final List<Point> tvlPoints=new ArrayList<>(),apyPoints=new ArrayList<>();
    public record Point(long time,Double value) {}
    public static Double value(JSONObject obj,String key) {
        if(obj.isNull(key))return null;
        double n=obj.optDouble(key,Double.NaN);return Double.isFinite(n)?n:null;
    }
    public static WidgetData parse(String raw) throws Exception {
        JSONObject json=new JSONObject(raw);
        int schema=json.getInt("schemaVersion");
        boolean legacy=schema==3 && LEGACY_SOURCE.equals(json.optString("source"));
        if(!legacy && (schema!=SCHEMA_VERSION || !SOURCE.equals(json.optString("source"))))
            throw new IllegalArgumentException("Неверный источник данных");
        WidgetData result=new WidgetData();result.generatedAt=json.getLong("generatedAt");
        result.start=json.getLong("rangeStart");result.end=json.getLong("rangeEnd");
        if(result.end-result.start!=30L*86400000)throw new IllegalArgumentException("Неверное окно графика");
        JSONObject current=json.getJSONObject("current");
        // Keep DeFiLlama during an upgrade, but never relabel the old history TVL as /proxy/apy.
        result.tvl=legacy?null:value(current,"tvl");result.apy=value(current,"apy");
        result.tvlAsOf=legacy?0:current.optLong("tvlAsOf",0);result.apyAsOf=current.optLong("apyAsOf",0);
        points(json.getJSONArray("apyPoints"),result.apyPoints,result.start,result.end);
        return result;
    }
    private static void points(JSONArray input,List<Point> output,long start,long end) throws Exception {
        if(input.length()>10000)throw new IllegalArgumentException("Слишком большой график");
        long previous=Long.MIN_VALUE;
        for(int i=0;i<input.length();i++) {
            JSONObject row=input.getJSONObject(i);long time=row.getLong("time");
            if(time<start || time>end || time<=previous)throw new IllegalArgumentException("Неверная дата графика");
            previous=time;output.add(new Point(time,value(row,"value")));
        }
    }
    public static boolean fresh(long timestamp,long now){return fresh(timestamp,now,MAX_AGE);}
    public static boolean fresh(long timestamp,long now,long maxAge){return timestamp>0 && timestamp<=now+300000 && now-timestamp<=maxAge;}
    public Double currentTvl(long now){return tvl;}
    public Double currentApy(long now){return apy;}
    /** Mean of the available source APYs in the snapshot's 30-day window, not a monthly return. */
    public Double averageApy30d() {
        BigDecimal sum=BigDecimal.ZERO;int count=0;
        for(Point point:apyPoints) {
            Double value=point.value();
            if(point.time()>=start && point.time()<=end && value!=null && Double.isFinite(value)) {
                sum=sum.add(BigDecimal.valueOf(value));count++;
            }
        }
        return count==0?null:sum.divide(BigDecimal.valueOf(count),MathContext.DECIMAL64).doubleValue();
    }
}
