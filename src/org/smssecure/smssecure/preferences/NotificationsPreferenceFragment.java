package org.smssecure.smssecure.preferences;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.smssecure.smssecure.ApplicationPreferencesActivity;
import org.smssecure.smssecure.R;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.notifications.NotificationChannels;
import org.smssecure.smssecure.util.SilencePreferences;

public class NotificationsPreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String TAG = NotificationsPreferenceFragment.class.getSimpleName();

  private static final int PICK_NOTIFICATION_RINGTONE = 5141;

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    masterSecret = getArguments().getParcelable("master_secret");

    this.findPreference(SilencePreferences.LED_COLOR_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(SilencePreferences.LED_BLINK_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    Preference ringtonePreference = findPreference(SilencePreferences.RINGTONE_PREF);

    if (ringtonePreference != null) {
      ringtonePreference.setOnPreferenceChangeListener(new RingtoneSummaryListener());
      ringtonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
          launchRingtonePicker();
          return true;
        }
      });

      initializeRingtoneSummary(ringtonePreference);
    }
    this.findPreference(SilencePreferences.REPEAT_ALERTS_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(SilencePreferences.NOTIFICATION_PRIVACY_PREF)
        .setOnPreferenceChangeListener(new NotificationPrivacyListener());

    initializeListSummary((ListPreference) findPreference(SilencePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(SilencePreferences.LED_BLINK_PREF));
    initializeListSummary((ListPreference) findPreference(SilencePreferences.REPEAT_ALERTS_PREF));
    initializeListSummary((ListPreference) findPreference(SilencePreferences.NOTIFICATION_PRIVACY_PREF));
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_notifications);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__notifications);
  }

  private void launchRingtonePicker() {
    Activity activity = getActivity();

    if (activity == null) {
      return;
    }

    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

    String current = SilencePreferences.getNotificationRingtone(activity);

    if (!TextUtils.isEmpty(current)) {
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current));
    }

    try {
      activity.startActivityForResult(intent, PICK_NOTIFICATION_RINGTONE);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "No ringtone picker available on this device.");
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != PICK_NOTIFICATION_RINGTONE || resultCode != Activity.RESULT_OK || data == null) {
      return;
    }

    Activity activity = getActivity();

    if (activity == null) {
      return;
    }

    Uri    picked  = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
    String toStore = picked != null ? picked.toString() : "";

    if (toStore.equals(SilencePreferences.getNotificationRingtone(activity))) {
      return;
    }

    Preference preference = findPreference(SilencePreferences.RINGTONE_PREF);

    if (preference == null) {
      return;
    }

    PreferenceManager.getDefaultSharedPreferences(activity)
                     .edit()
                     .putString(SilencePreferences.RINGTONE_PREF, toStore)
                     .apply();

    RingtoneSummaryListener listener = (RingtoneSummaryListener) preference.getOnPreferenceChangeListener();

    if (listener != null) {
      listener.onPreferenceChange(preference, picked);
    }

    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannels.updateMessagesChannel(activity);
    }
  }

  private class RingtoneSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      Uri value = (Uri) newValue;

      if (value == null) {
        preference.setSummary(R.string.preferences__silent);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), value);
        if (tone != null) {
          preference.setSummary(tone.getTitle(getActivity()));
        }
      }

      return true;
    }
  }

  private void initializeRingtoneSummary(Preference pref) {
    RingtoneSummaryListener listener = (RingtoneSummaryListener) pref.getOnPreferenceChangeListener();
    Activity                activity = getActivity();

    if (activity == null || listener == null) {
      return;
    }

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
    String            encodedUri        = sharedPreferences.getString(pref.getKey(), null);
    Uri               uri;

    if (encodedUri == null) {
      uri = Settings.System.DEFAULT_NOTIFICATION_URI;
    } else {
      uri = !TextUtils.isEmpty(encodedUri) ? Uri.parse(encodedUri) : null;
    }

    listener.onPreferenceChange(pref, uri);
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId   = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId  = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(SilencePreferences.isNotificationsEnabled(context) ? onCapsResId : offCapsResId);
  }

  private class NotificationPrivacyListener extends ListSummaryListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MessageNotifier.updateNotification(getActivity(), masterSecret);
          return null;
        }
      }.execute();

      return super.onPreferenceChange(preference, value);
    }

  }
}
