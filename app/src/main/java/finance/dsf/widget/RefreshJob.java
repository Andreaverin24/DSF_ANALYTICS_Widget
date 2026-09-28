package finance.dsf.widget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RefreshJob extends JobService {
    static final int PERIODIC=4101, ONCE=4102;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    // Job lifecycle callbacks and completion bookkeeping all run on the main thread.
    private final Map<Integer,Run> work=new HashMap<>();
    private static final class Run {
        final JobParameters params;
        Future<?> future;
        AnalyticsRepository.Downloaded downloaded;
        String error;
        Run(JobParameters params) { this.params=params; }
    }
    public static void schedule(Context context, boolean immediate) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        ComponentName component=new ComponentName(context,RefreshJob.class);
        JobInfo periodic=scheduler.getPendingJob(PERIODIC);
        if(AnalyticsWidget.ids(context).length>0 && (periodic==null || periodic.getInitialBackoffMillis()!=60000)) {
            scheduler.schedule(new JobInfo.Builder(PERIODIC,component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60000L,5*60000L)
                .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true).build());
        }
        JobInfo once=scheduler.getPendingJob(ONCE);
        if(immediate && (once==null || !once.isPersisted())) scheduler.schedule(new JobInfo.Builder(ONCE,component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0)
            .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
    }
    static void cancel(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        scheduler.cancel(PERIODIC); scheduler.cancel(ONCE);
    }
    @Override public boolean onStartJob(JobParameters params) {
        Run run=new Run(params);
        work.put(params.getJobId(),run);
        run.future=EXECUTOR.submit(()->{
            try {
                WidgetData cached=SnapshotStore.read(this);
                if(cached==null || cached.tvl==null || cached.apy==null || !SnapshotStore.error(this).isEmpty()
                    || System.currentTimeMillis()-cached.generatedAt>60000)
                    run.downloaded=AnalyticsRepository.fetch(getApplicationContext());
            }
            catch(Exception error) {
                if(!Thread.currentThread().isInterrupted()) {
                    String message=error.getMessage();
                    run.error=message==null?"Не удалось обновить данные":message;
                }
            } finally {
                main.post(()->finish(run));
            }
        });
        return true;
    }
    private void finish(Run run) {
        if(work.get(run.params.getJobId())!=run)return;
        work.remove(run.params.getJobId());
        // Commit only while this run is still active. onStopJob cannot interleave here.
        try {
            if(run.downloaded!=null) {
                SnapshotStore.save(this,run.downloaded.json());
                SnapshotStore.error(this,run.downloaded.error());
            } else if(run.error!=null)SnapshotStore.error(this,run.error);
        } catch(Exception error) {
            SnapshotStore.error(this,"Не удалось сохранить данные");
        }
        WidgetData latest=SnapshotStore.read(this);
        boolean retry=latest==null || latest.tvl==null || latest.apy==null || !SnapshotStore.error(this).isEmpty();
        AnalyticsWidget.updateAll(getApplicationContext(),true);
        jobFinished(run.params,retry && AnalyticsWidget.ids(this).length>0);
    }
    @Override public boolean onStopJob(JobParameters params) {
        Run run=work.remove(params.getJobId());
        if(run!=null)run.future.cancel(true);
        return AnalyticsWidget.ids(this).length>0;
    }
    @Override public void onDestroy() {
        for(Run run:work.values())run.future.cancel(true);
        work.clear();
        super.onDestroy();
    }
}
