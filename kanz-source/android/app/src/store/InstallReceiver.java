package store.almasdar;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
public class InstallReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    MainActivity.ensureLang(c);
    int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
    if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent a = i.getParcelableExtra(Intent.EXTRA_INTENT);
      if (a == null) return;
      a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (Dl.live != null && Dl.live.resumed) c.startActivity(a);
      else Dl.notifyUser(c, 4242, MainActivity.tr("تأكيد تثبيت تطبيق"), MainActivity.tr("اضغط لتأكيد التثبيت"), PendingIntent.getActivity(c, 4242, a, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    } else if (st == PackageInstaller.STATUS_SUCCESS) {
      Toast.makeText(c, MainActivity.tr("تم التثبيت بنجاح ✅"), Toast.LENGTH_LONG).show();
    } else {
      String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
      Toast.makeText(c, MainActivity.tr("فشل التثبيت: ") + m, Toast.LENGTH_LONG).show();
    }
  }
}
