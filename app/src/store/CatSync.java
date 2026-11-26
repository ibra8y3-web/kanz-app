package store.almasdar;

import android.app.job.*;
import android.content.*;
import java.io.*;
import java.net.*;
import org.json.*;

/** Hourly background catalog fetch (any network unless the hidden data-saver switch is on).
 *  It only downloads the new catalog into pre.gz; the app applies it instantly the next time it opens. */
public class CatSync extends JobService {
  static final int JOB = 78;

  static void schedule(Context c) {
    try {
      JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      SharedPreferences sp = c.getSharedPreferences("s", 0);
      int net = sp.getBoolean("limdata", false) ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY;
      for (JobInfo p : js.getAllPendingJobs()) if (p.getId() == JOB && p.getNetworkType() == net) return;
      js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, CatSync.class)).setRequiredNetworkType(net).setPeriodic(60L * 60 * 1000).setPersisted(true).build());
    } catch (Exception e) { }
  }

  @Override public boolean onStartJob(final JobParameters p) {
    new Thread(() -> { try { run(getApplicationContext()); } catch (Throwable e) { } jobFinished(p, false); }).start();
    return true;
  }
  @Override public boolean onStopJob(JobParameters p) { return true; }

  static byte[] get(String url) throws IOException {
    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection(); cn.setConnectTimeout(15000); cn.setReadTimeout(30000); cn.setRequestProperty("User-Agent", "Kanz-Store");
    if (cn.getResponseCode() != 200) throw new IOException("HTTP " + cn.getResponseCode());
    InputStream in = cn.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close(); return bo.toByteArray();
  }

  static void run(Context c) throws Exception {
    SharedPreferences sp = c.getSharedPreferences("s", 0);
    String base = sp.getString("catbase", MainActivity.DEF_BASE); if (!base.endsWith("/")) base += "/";
    JSONObject m = new JSONObject(new String(get(base + "meta.json"), "UTF-8"));
    long rev = m.optLong("rev"), local = 0;
    File rcf = new File(c.getFilesDir(), "rc2.jsonl");
    if (rcf.exists()) { try { BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(rcf), "UTF-8")); local = new JSONObject(r.readLine()).optLong("rev"); r.close(); } catch (Exception e) { local = 0; } }
    File pf = new File(c.getFilesDir(), "pre.gz");
    if (rev == local && local > 0) return;
    if (pf.exists() && sp.getLong("prerev", -1) == rev) return;
    boolean delta = local > 0 && local >= m.optLong("delta_base", Long.MAX_VALUE);
    byte[] gz = get(base + (delta ? "delta.json.gz" : "catalog.json.gz"));
    File t = new File(pf.getPath() + ".t"); FileOutputStream o = new FileOutputStream(t); o.write(gz); o.close(); t.renameTo(pf);
    sp.edit().putLong("prerev", rev).putBoolean("predelta", delta).apply();
  }
}
