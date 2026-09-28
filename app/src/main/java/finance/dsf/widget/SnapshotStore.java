package finance.dsf.widget;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class SnapshotStore {
    static final String UPDATED="snapshot_updated";
    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "snapshot.json")); }
    static WidgetData read(Context context) {
        try { return WidgetData.parse(new String(file(context).readFully(), StandardCharsets.UTF_8)); }
        catch (Exception error) { return null; }
    }
    static void save(Context context, String json) throws Exception {
        WidgetData.parse(json);
        AtomicFile store=file(context);
        FileOutputStream out=null;
        try { out=store.startWrite(); out.write(json.getBytes(StandardCharsets.UTF_8)); store.finishWrite(out); }
        catch (Exception error) { if(out!=null) store.failWrite(out); throw error; }
        // An invalidation signal only: the atomic JSON file remains the data source.
        context.getSharedPreferences("sync",0).edit().putLong(UPDATED,System.nanoTime()).apply();
    }
    static void error(Context context, String message) {
        context.getSharedPreferences("sync",0).edit().putString("error",message).apply();
    }
    static String error(Context context) { return context.getSharedPreferences("sync",0).getString("error", ""); }
}
