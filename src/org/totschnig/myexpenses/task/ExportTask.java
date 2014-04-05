package org.totschnig.myexpenses.task;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.Export;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Account.ExportFormat;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.Utils;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class ExportTask extends AsyncTask<Void, String, ArrayList<File>> {
  private final TaskExecutionFragment taskExecutionFragment;
  //we store the label of the account as progress
  private String progress ="";
  private final ArrayList<File> result = new ArrayList<File>();
  private Account.ExportFormat format;
  private boolean deleteP;
  private boolean notYetExportedP;
  private String dateFormat;
  private char decimalSeparator;
  private long accountId;
  private String currency;

  /**
   * @param args 
   * @param context
   * @param source Source for the import
   */
  public ExportTask(TaskExecutionFragment taskExecutionFragment, Bundle extras) {
    this.taskExecutionFragment = taskExecutionFragment;
    deleteP = extras.getBoolean("deleteP");
    notYetExportedP = extras.getBoolean("notYetExportedP");
    dateFormat = extras.getString("dateFormat");
    decimalSeparator = extras.getChar("decimalSeparator");
    currency = extras.getString(KEY_CURRENCY);
    if (deleteP && notYetExportedP)
      throw new IllegalStateException(
          "Deleting exported transactions is only allowed when all transactions are exported");
    try {
      format = ExportFormat.valueOf(extras.getString("format"));
    } catch (IllegalArgumentException e) {
      format = ExportFormat.QIF;
    }
    accountId = extras.getLong(KEY_ROWID);
    
  }
  String getProgress() {
    return progress;
  }
  void appendToProgress(String progress) {
    this.progress += "\n" + progress;
  }

  /* (non-Javadoc)
   * updates the progress dialog
   * @see android.os.AsyncTask#onProgressUpdate(Progress[])
   */
  protected void onProgressUpdate(String... values) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      for (String progress: values) {
        this.taskExecutionFragment.mCallbacks.onProgressUpdate(progress);
      }
    }
  }
  @Override
  protected void onPostExecute(ArrayList<File>  result) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onPostExecute(
          TaskExecutionFragment.TASK_QIF_IMPORT, result);
    }
  }

  /* (non-Javadoc)
   * this is where the bulk of the work is done via calls to {@link #importCatsMain()}
   * and {@link #importCatsSub()}
   * sets up {@link #categories} and {@link #sub_categories}
   * @see android.os.AsyncTask#doInBackground(Params[])
   */
  @Override
  protected ArrayList<File> doInBackground(Void... ignored) {
    Long[] accountIds;
    if (accountId > 0L) {
        accountIds = new Long[] {accountId};
    } else {
      String selection = null;
      String[] selectionArgs = null;
      if (currency != null) {
        selection = DatabaseConstants.KEY_CURRENCY + " = ?";
        selectionArgs = new String[]{currency};
      }
      Cursor c = MyApplication.getInstance().getContentResolver().query(TransactionProvider.ACCOUNTS_URI,
          new String[] {KEY_ROWID}, selection, selectionArgs, null);
      accountIds = DbUtils.getLongArrayFromCursor(c, KEY_ROWID);
    }
    Account account;
    File destDir;
    File appDir = Utils.requireAppDir();
    if (appDir == null) {
      publishProgress(MyApplication.getInstance().getString(R.string.external_storage_unavailable));
      return(null);
    }
    if (accountIds.length > 1) {
      String now = new SimpleDateFormat("yyyMMdd-HHmmss",Locale.US).format(new Date());
      destDir = new File(appDir,"export-" + now);
      if (destDir.exists()) {
        publishProgress(String.format(MyApplication.getInstance().getString(R.string.export_expenses_outputfile_exists), destDir.getAbsolutePath()));
        return(null);
      }
      destDir.mkdir();
    } else
      destDir = appDir;
    ArrayList<Account> successfullyExported = new ArrayList<Account>();
    for (Long id : accountIds) {
      account = Account.getInstanceFromDb(id);
      publishProgress(account.label + " ...");
      try {
        Result result = account.exportAll(destDir,format,notYetExportedP,dateFormat,decimalSeparator);
        File output = null;
        String progressMsg;
        if (result.extra != null) {
          output = (File) result.extra[0];
          progressMsg = MyApplication.getInstance().getString(result.getMessage(), output.getAbsolutePath());
        } else {
          progressMsg = MyApplication.getInstance().getString(result.getMessage());
        }
        publishProgress("... " + progressMsg);
        if (result.success) {
          SharedPreferences settings = MyApplication.getInstance().getSettings();
          if (settings.getBoolean(MyApplication.PREFKEY_PERFORM_SHARE,false)) {
            addResult(output);
          }
          successfullyExported.add(account);
        }
      } catch (IOException e) {
        Log.e("MyExpenses",e.getMessage());
        publishProgress("... " + MyApplication.getInstance().getString(R.string.export_expenses_sdcard_failure));
      }
    }
    for (Account a : successfullyExported) {
      if (deleteP)
        a.reset();
      else
        a.markAsExported();
    }
    return getResult();
  }
  public ArrayList<File> getResult() {
    return result;
  }
  public void addResult(File file) {
    result.add(file);
  }
}
