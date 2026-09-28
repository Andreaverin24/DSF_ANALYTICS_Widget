package finance.dsf.widget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class RefreshJobDeviceTest {
    @Test public void addingWidgetSchedulesPersistentWorkWithoutActivity() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(AnalyticsWidget.ids(app).length>0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            JobScheduler scheduler=app.getSystemService(JobScheduler.class);
            RefreshJob.cancel(app);
            try {
                new AnalyticsWidget().onUpdate(app,AppWidgetManager.getInstance(app),AnalyticsWidget.ids(app));
                JobInfo periodic=scheduler.getPendingJob(RefreshJob.PERIODIC);
                JobInfo once=scheduler.getPendingJob(RefreshJob.ONCE);
                assertNotNull(periodic);assertNotNull(once);
                assertTrue(periodic.isPersisted());assertTrue(periodic.isPeriodic());
                assertEquals(30*60000L,periodic.getIntervalMillis());
                assertTrue(once.isPersisted());assertFalse(once.isPeriodic());
                assertEquals(JobInfo.NETWORK_TYPE_ANY,once.getNetworkType());
                assertEquals(60000,once.getInitialBackoffMillis());
                assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL,once.getBackoffPolicy());
            } finally { RefreshJob.schedule(app,true); }
        });
    }

    @Test public void repeatedWidgetBootAndPackageEventsPreservePendingRetry() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(AnalyticsWidget.ids(app).length>0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            JobScheduler scheduler=app.getSystemService(JobScheduler.class);
            // A delayed request stands in for a retry whose backoff must not be reset.
            JobInfo pending=new JobInfo.Builder(RefreshJob.ONCE,new ComponentName(app,RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(30*60000L)
                .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build();
            assertEquals(JobScheduler.RESULT_SUCCESS,scheduler.schedule(pending));
            try {
                AnalyticsWidget receiver=new AnalyticsWidget();
                for(String action:new String[]{Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED})
                    receiver.onReceive(app,new Intent(action));
                receiver.onUpdate(app,AppWidgetManager.getInstance(app),AnalyticsWidget.ids(app));
                RefreshJob.schedule(app,true);
                JobInfo after=scheduler.getPendingJob(RefreshJob.ONCE);
                assertNotNull(after);
                assertEquals(30*60000L,after.getMinLatencyMillis());
            } finally {
                scheduler.cancel(RefreshJob.ONCE);
                RefreshJob.schedule(app,true);
            }
        });
    }
}
