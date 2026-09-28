package finance.dsf.widget;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Current TVL from DSF /proxy/apy; unmodified APY history from DeFiLlama. */
final class MarketHistory {
    record Result(String json,List<String> errors) {}
    private record Series(Double latest,long asOf,JSONArray points) {}
    static Result snapshot(String dsf,String llama,WidgetData previous,long now) throws Exception {
        long start=now-30L*86400000;
        Series tvl=new Series(previous==null?null:previous.tvl,previous==null?0:previous.tvlAsOf,new JSONArray());
        Series apy=cachedApy(previous,start,now);
        List<String> errors=new ArrayList<>();
        if(dsf!=null)try {tvl=readTvl(dsf,now);}catch(Exception e){errors.add("TVL: "+e.getMessage());}
        if(llama!=null)try {apy=readApy(llama,start,now);}catch(Exception e){errors.add("APY: "+e.getMessage());}
        JSONObject current=new JSONObject().put("tvl",nullable(tvl.latest)).put("tvlAsOf",tvl.asOf)
            .put("apy",nullable(apy.latest)).put("apyAsOf",apy.asOf);
        return new Result(new JSONObject().put("schemaVersion",WidgetData.SCHEMA_VERSION).put("source",WidgetData.SOURCE).put("generatedAt",now)
            .put("rangeStart",start).put("rangeEnd",now).put("current",current)
            .put("tvlPoints",tvl.points).put("apyPoints",apy.points).toString(),errors);
    }
    private static Object nullable(Double value){return value==null?JSONObject.NULL:value;}
    private static JSONObject point(long time,Double value) throws Exception {
        return new JSONObject().put("time",time).put("value",nullable(value));
    }
    private static Series cachedApy(WidgetData previous,long start,long now) throws Exception {
        JSONArray points=new JSONArray();
        if(previous==null)return new Series(null,0,points);
        for(WidgetData.Point row:previous.apyPoints)
            if(row.time()>=start && row.time()<=now)points.put(point(row.time(),row.value()));
        return new Series(previous.apy,previous.apyAsOf,points);
    }
    private static Series readTvl(String raw,long receivedAt) throws Exception {
        Object rawValue=new JSONObject(raw).opt("tvl");
        if(!(rawValue instanceof Number))throw new IllegalArgumentException("нет числового tvl");
        double value=((Number)rawValue).doubleValue();
        if(!Double.isFinite(value) || value<0)throw new IllegalArgumentException("неверное tvl");
        // The endpoint has no source timestamp. Receipt time must not imply on-chain freshness.
        return new Series(value,receivedAt,new JSONArray());
    }
    private static Series readApy(String raw,long start,long now) throws Exception {
        JSONObject root=new JSONObject(raw);
        if(!"success".equals(root.optString("status")))throw new IllegalArgumentException("неверный ответ DeFiLlama");
        JSONArray rows=root.getJSONArray("data");
        if(rows.length()==0 || rows.length()>10000)throw new IllegalArgumentException("нет истории");
        TreeMap<Long,Double> sorted=new TreeMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            long time=Instant.parse(row.getString("timestamp")).toEpochMilli();
            if(time>now || time<start)continue;
            Double value=WidgetData.value(row,"apy");
            if(sorted.containsKey(time) && !java.util.Objects.equals(sorted.get(time),value))
                throw new IllegalArgumentException("конфликт снимков");
            sorted.put(time,value);
        }
        Double latest=null;long asOf=0;JSONArray points=new JSONArray();
        for(Map.Entry<Long,Double> row:sorted.entrySet()) {
            points.put(point(row.getKey(),row.getValue()));
            if(row.getValue()!=null){latest=row.getValue();asOf=row.getKey();}
        }
        if(latest==null)throw new IllegalArgumentException("нет значений за 30 дней");
        return new Series(latest,asOf,points);
    }
}
