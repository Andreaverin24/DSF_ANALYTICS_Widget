package finance.dsf.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class MainActivityDeviceTest {
    @Test public void openScreenReadsCacheAndReactsToACompletedSnapshotWrite() {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context app=instrumentation.getTargetContext();
        Activity activity=instrumentation.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{
                try {
                    // Re-save the exact current file on the main thread; no fixture replaces real data.
                    String raw=new String(Files.readAllBytes(new File(app.getFilesDir(),"snapshot.json").toPath()),StandardCharsets.UTF_8);
                    WidgetData data=WidgetData.parse(raw);
                    TextView average=activity.findViewById(R.id.stats_average_apy);
                    TextView current=activity.findViewById(R.id.stats_current_apy);
                    TextView tvl=activity.findViewById(R.id.stats_tvl);
                    assertEquals(ChartRenderer.percent(data.averageApy30d()),average.getText().toString());
                    assertEquals(ChartRenderer.percent(data.apy),current.getText().toString());
                    assertEquals(ChartRenderer.money(data.tvl),tvl.getText().toString());
                    average.setText("…");current.setText("…");tvl.setText("…");
                    SnapshotStore.save(app,raw);
                    assertEquals(ChartRenderer.percent(data.averageApy30d()),average.getText().toString());
                    assertEquals(ChartRenderer.percent(data.apy),current.getText().toString());
                    assertEquals(ChartRenderer.money(data.tvl),tvl.getText().toString());
                } catch(Exception error) {throw new AssertionError(error);}
            });
        } finally {instrumentation.runOnMainSync(activity::finish);}
    }
}
