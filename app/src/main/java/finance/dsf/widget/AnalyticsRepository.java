package finance.dsf.widget;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class AnalyticsRepository {
    static final String SITE="https://dsf-analytics-mk2.vercel.app";
    static final String TVL="https://watchdog.dsf.finance/proxy/apy";
    static final String LLAMA="https://yields.llama.fi/chart/774082cb-0775-4718-bac0-fde0165c8f9c";
    static final int MAX_RESPONSE=16*1024*1024;

    static String download(String endpoint,boolean dsf) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(false);
        if(dsf) {
            connection.setRequestProperty("Origin",SITE);
            connection.setRequestProperty("Referer",SITE+"/");
        }
        connection.setRequestProperty("User-Agent","DSF-Analytics-Widget/0.3 (Android; first-party read-only client)");
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Accept-Encoding","gzip");
        try {
            int status=connection.getResponseCode();
            if(status!=200) throw new IllegalStateException((dsf?"DSF API":"DeFiLlama")+": HTTP "+status);
            InputStream stream=connection.getInputStream();
            if("gzip".equalsIgnoreCase(connection.getContentEncoding())) stream=new GZIPInputStream(stream);
            try(InputStream input=stream; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16384]; int count;
                while((count=input.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(output.size()+count>MAX_RESPONSE)throw new IllegalStateException("Ответ API слишком большой");
                    output.write(buffer,0,count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }

    record Downloaded(String json,String error) {}
    static Downloaded fetch(Context context) throws Exception {
        WidgetData previous=SnapshotStore.read(context);
        String apy=null,tvl=null;java.util.List<String> errors=new java.util.ArrayList<>();
        try {apy=download(LLAMA,false);}catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;errors.add("APY: "+message(e));}
        try {tvl=download(TVL,true);}catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;errors.add("TVL: "+message(e));}
        MarketHistory.Result result=MarketHistory.snapshot(tvl,apy,previous,System.currentTimeMillis());
        errors.addAll(result.errors());
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        return new Downloaded(result.json(),String.join("\n",errors));
    }
    private static String message(Exception e){return e.getMessage()==null?"Нет соединения":e.getMessage();}
}
