package finance.dsf.widget;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Only DSF's versioned HTTPS APKs may become download actions. */
record UpdateInfo(int versionCode,String versionName,int minSdk,String apkUrl,String sha256) {
    static final long CHECK_INTERVAL=24L*60*60*1000;
    static final long CACHE_LIFETIME=7*CHECK_INTERVAL;

    static UpdateInfo parse(String raw) throws Exception {
        Map<String,String> fields=new HashMap<>();Set<String> names=new HashSet<>();
        try(JsonReader reader=new JsonReader(new StringReader(raw))) {
            reader.setLenient(false);reader.beginObject();
            while(reader.hasNext()) {
                String key=reader.nextName();
                if(!names.add(key))throw new IllegalArgumentException("Duplicate field");
                boolean number=key.equals("schemaVersion") || key.equals("versionCode") || key.equals("minSdk");
                boolean text=key.equals("applicationId") || key.equals("versionName") || key.equals("apkUrl") || key.equals("sha256");
                if(number || text) {
                    if(reader.peek()!=(number?JsonToken.NUMBER:JsonToken.STRING))throw new IllegalArgumentException(key);
                    fields.put(key,reader.nextString());
                } else reader.skipValue();
            }
            reader.endObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing data");
        }
        if(integer(fields,"schemaVersion")!=1 || !"finance.dsf.widget".equals(fields.get("applicationId")))
            throw new IllegalArgumentException("Unsupported update manifest");
        return validated(integer(fields,"versionCode"),fields.get("versionName"),integer(fields,"minSdk"),
            fields.get("apkUrl"),fields.get("sha256"));
    }
    static UpdateInfo validated(int code,String name,int sdk,String url,String hash) {
        if(code<=0 || sdk<26 || sdk>1000 || name==null
            || !name.matches("[0-9]{1,5}(\\.[0-9]{1,5}){1,3}")
            || !("https://dsf.finance/downloads/DSF-Finance-Widget-"+name+".apk").equals(url)
            || hash==null || !hash.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Invalid update manifest");
        return new UpdateInfo(code,name,sdk,url,hash);
    }
    boolean available(int installedCode,int deviceSdk) {return versionCode>installedCode && minSdk<=deviceSdk;}
    static boolean due(long now,long attemptedAt) {
        return attemptedAt<=0 || now<attemptedAt || now-attemptedAt>=CHECK_INTERVAL;
    }
    static boolean fresh(long now,long checkedAt) {
        return checkedAt>0 && now>=checkedAt && now-checkedAt<CACHE_LIFETIME;
    }
    private static int integer(Map<String,String> fields,String key) {
        String value=fields.get(key);
        if(value==null || !value.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException(key);
        return Integer.parseInt(value);
    }
}
