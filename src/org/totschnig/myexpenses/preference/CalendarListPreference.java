package org.totschnig.myexpenses.preference;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.MyPreferenceActivity;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.ui.SimpleCursorAdapter;

import com.android.calendar.CalendarContractCompat;
import com.android.calendar.CalendarContractCompat.Calendars;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Build;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class CalendarListPreference extends ListPreference {
  public CalendarListPreference(Context context) {
    super(context);
  }
  public CalendarListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  @Override
  protected void onPrepareDialogBuilder( AlertDialog.Builder builder ) {
    boolean localExists = false;
    Cursor selectionCursor;
    String value = getSharedPreferences().getString(getKey(), "-1");
    int selectedIndex = -1;
    String[] projection =
      new String[]{
            Calendars._ID,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.NAME,
            "ifnull(" + Calendars.ACCOUNT_NAME + ",'') || ' / ' ||" +
            "ifnull(" + Calendars.CALENDAR_DISPLAY_NAME + ",'') AS full_name"
    };
    Cursor calCursor = null;
    try {
      calCursor = getContext().getContentResolver().
          query(Calendars.CONTENT_URI,
              projection,
              Calendars.CALENDAR_ACCESS_LEVEL + " >= " + Calendars.CAL_ACCESS_CONTRIBUTOR,
              null,
              Calendars._ID + " ASC");
    } catch (SecurityException e) {
      // android.permission.READ_CALENDAR or android.permission.WRITE_CALENDAR missing
    }
    if (calCursor != null) {
      if (calCursor.moveToFirst()) {
        do {
          if (calCursor.getString(0).equals(value)) {
            selectedIndex = calCursor.getPosition();
          }
          if (DbUtils.getString(calCursor,1).equals(MyApplication.PLANNER_ACCOUNT_NAME)
              && DbUtils.getString(calCursor,2).equals(CalendarContractCompat.ACCOUNT_TYPE_LOCAL)
              && DbUtils.getString(calCursor,3).equals(MyApplication.PLANNER_CALENDAR_NAME))
            localExists = true;
        } while (calCursor.moveToNext());
      }
      if (localExists || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
        selectionCursor = calCursor;
      } else {
        MatrixCursor extras = new MatrixCursor(new String[] {
            Calendars._ID,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.NAME,
            "full_name"});
        extras.addRow(new String[] {
            "-1", "","","",
            getContext().getString(R.string.pref_planning_calendar_create_local) });
        selectionCursor = new MergeCursor(new Cursor[] {calCursor,extras});
      }
      builder.setSingleChoiceItems(
          new SimpleCursorAdapter(getContext(), android.R.layout.select_dialog_singlechoice,
              selectionCursor, new String[]{"full_name"}, new int[]{android.R.id.text1},0),
          selectedIndex,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              long itemId = ((AlertDialog) dialog).getListView().getItemIdAtPosition(which);
              if (itemId == -1) {
                ((MyPreferenceActivity) getContext()).showDialog(R.id.PLANNER_SETUP_INFO_CREATE_NEW_WARNING_DIALOG);
              }
              else if (callChangeListener(itemId)) {
                setValue(String.valueOf(itemId));
              }
                /*
                 * Clicking on an item simulates the positive button
                 * click, and dismisses the dialog.
                 */
                CalendarListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
          });
    } else {
      builder.setMessage("Calendar provider not available");
    }
    builder.setPositiveButton( null, null );
  }
  @Override
  protected void onDialogClosed(boolean positiveResult) {
    if (positiveResult)
      ((MyPreferenceActivity) getContext()).onCalendarListPreferenceSet();
  }
  public void show()
  {
      showDialog(null);
  }
}
