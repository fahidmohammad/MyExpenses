/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * Util class with helper methods
 * @author Michael Totschnig
 *
 */
public class Utils {
  public static Integer usagesLeft(String feature) {
    return MyApplication.USAGES_LIMIT - MyApplication.db().getContribFeatureUsages(feature);
  }
  public static void recordUsage(String feature) {
    if (!MyApplication.getInstance().isContribEnabled)
      MyApplication.db().incrFeatureUsages(feature);
  }  
  public static String getDefaultDecimalSeparator() {
    String sep = ".";
    int sdk =  Build.VERSION.SDK_INT;
    //there are different intricacies of bug http://code.google.com/p/android/issues/detail?id=2626
    //on Gingerbread, the numeric keyboard of the default input method
    //does not have a , thus we default to . as decimal separator
    if (sdk == 8 || sdk == 9) {
      return sep;
    }
   NumberFormat nfDLocal = NumberFormat.getNumberInstance();
    if (nfDLocal instanceof DecimalFormat) {
      DecimalFormatSymbols symbols = ((DecimalFormat)nfDLocal).getDecimalFormatSymbols();
      sep=String.valueOf(symbols.getDecimalSeparator());
    }
    return sep;
  }
  
  /**
   * <a href="http://www.ibm.com/developerworks/java/library/j-numberformat/">http://www.ibm.com/developerworks/java/library/j-numberformat/</a>
   * @param strFloat parsed as float with the number format defined in the locale
   * @return the float retrieved from the string or null if parse did not succeed
   */
  public static BigDecimal validateNumber(DecimalFormat df, String strFloat) {
    ParsePosition pp;
    pp = new ParsePosition( 0 );
    pp.setIndex( 0 );
    df.setParseBigDecimal(true);
    BigDecimal n = (BigDecimal) df.parse(strFloat,pp);
    if( strFloat.length() != pp.getIndex() || 
        n == null )
    {
      return null;
    } else {
      return n;
    }
  }
  
  public static URI validateUri(String target) {
    boolean targetParsable;
    URI uri = null;
    if (!target.equals("")) {
      try {
        uri = new URI(target);
        String scheme = uri.getScheme();
        //strangely for mailto URIs getHost returns null, so we make sure that mailto URIs handled as valid
        targetParsable = scheme != null && (scheme.equals("mailto") || uri.getHost() != null);
      } catch (URISyntaxException e1) {
        targetParsable = false;
      }
      if (!targetParsable) {
        return null;
      }
      return uri;
    }
    return null;
  }
  
  /**
   * formats an amount with a currency
   * @param amount
   * @param currency
   * @return formated string
   */
  static String formatCurrency(Money money) {
    BigDecimal amount = money.getAmountMajor();
    Currency currency = money.getCurrency();
    return formatCurrency(amount,currency);
  }
  static String formatCurrency(BigDecimal amount, Currency currency) {
    NumberFormat nf = NumberFormat.getCurrencyInstance();
    int fractionDigits = currency.getDefaultFractionDigits();
    nf.setCurrency(currency);
    nf.setMinimumFractionDigits(fractionDigits);
    nf.setMaximumFractionDigits(fractionDigits);
    return nf.format(amount);
  }
  /**
   * utility method that calls formatters for date
   * @param text
   * @return formated string
   */
  static String convDate(String text, SimpleDateFormat format) {
    return format.format(Timestamp.valueOf(text));
  }
  /**
   * utility method that calls formatters for amount
   * @param text amount as String retrieved from DB (stored as int minor unit)
   * @param currency 
   * @return formated string
   */
  static String convAmount(String text, Currency currency) {
    return formatCurrency(new Money(currency,Long.valueOf(text)));
  }
  //TODO: create generic function
  static String[] getStringArrayFromCursor(Cursor c, String field) {
    String[] result = new String[c.getCount()];
    if(c.moveToFirst()){
     for (int i = 0; i < c.getCount(); i++){
       result[i] = c.getString(c.getColumnIndex(field));
       c.moveToNext();
     }
    }
    return result;
  }
  static Long[] getLongArrayFromCursor(Cursor c, String field) {
    Long[] result = new Long[c.getCount()];
    if(c.moveToFirst()){
     for (int i = 0; i < c.getCount(); i++){
       result[i] = c.getLong(c.getColumnIndex(field));
       c.moveToNext();
     }
    }
    return result;
  }
  
  /**
   * @return directory for storing backups and exports, null if external storage is not available
   */
  static File requireAppDir() {
    if (!isExternalStorageAvailable())
      return null;
    File sd = Environment.getExternalStorageDirectory();
    File appDir = new File(sd, "myexpenses");
    appDir.mkdir();
    return appDir;
  }
  /**
   * Helper Method to Test if external Storage is Available
   * from http://www.ibm.com/developerworks/xml/library/x-androidstorage/index.html
   */
  static boolean isExternalStorageAvailable() {
      boolean state = false;
      String extStorageState = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
          state = true;
      }
      return state;
  }
  
  static boolean copy(File src, File dst) {
    FileChannel srcC;
    try {
      srcC = new FileInputStream(src).getChannel();
      FileChannel dstC = new FileOutputStream(dst).getChannel();
      dstC.transferFrom(srcC, 0, srcC.size());
      srcC.close();
      dstC.close();
      return true;
    } catch (FileNotFoundException e) {
      Log.e("MyExpenses",e.getLocalizedMessage());
    } catch (IOException e) {
      Log.e("MyExpenses",e.getLocalizedMessage());
    }
    return false;
  }

  public static void setBackgroundFilter(View v, int c) {
    v.getBackground().setColorFilter(c,PorterDuff.Mode.MULTIPLY);
  }
  /**
   * Indicates whether the specified action can be used as an intent. This
   * method queries the package manager for installed packages that can
   * respond to an intent with the specified action. If no suitable package is
   * found, this method returns false.
   *
   * From http://android-developers.blogspot.fr/2009/01/can-i-use-this-intent.html
   *
   * @param context The application's environment.
   * @param action The Intent action to check for availability.
   *
   * @return True if an Intent with the specified action can be sent and
   *         responded to, false otherwise.
   */
  public static boolean isIntentAvailable(Context context, Intent intent) {
      final PackageManager packageManager = context.getPackageManager();
      List<ResolveInfo> list =
              packageManager.queryIntentActivities(intent,
                      PackageManager.MATCH_DEFAULT_ONLY);
      return list.size() > 0;
  }
  public static boolean doesPackageExist(Context context,String targetPackage){
    PackageManager pm=context.getPackageManager();
    try {
     PackageInfo info=pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
     return false;
     }  
     return true;
    }

  public static int getTextColorForBackground(int color) {
    int greyLevel = (int) (0.299 * Color.red(color)
        + 0.587 * Color.green(color)
        + 0.114 * Color.blue(color));
    return greyLevel > 127 ? Color.BLACK : Color.WHITE;
  }
  public static boolean verifyLicenceKey (String key) {
    String s = Secure.getString(MyApplication.getInstance().getContentResolver(),Secure.ANDROID_ID) + 
        MyApplication.CONTRIB_SECRET;
    Long l = (s.hashCode() & 0x00000000ffffffffL);
    return l.toString().equals(key);
  }
  public static void viewContribApp(Activity ctx) {
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse("market://details?id=org.totschnig.myexpenses.contrib"));
    if (Utils.isIntentAvailable(ctx,i)) {
      ctx.startActivity(i);
    } else {
      Toast.makeText(ctx.getBaseContext(),R.string.error_accessing_gplay, Toast.LENGTH_LONG).show();
    }
  }
  public static String getContribFeatureLabelsAsFormattedList(Context ctx) {
    String result = " - " + ctx.getString(R.string.contrib_feature_aggregate_label) +"<br>";
    result += " - " + ctx.getString(R.string.contrib_feature_edit_template_label) +"<br>";
    result += " - " + ctx.getString(R.string.contrib_feature_restore_label);
    return result;
  }
  public static String md5(String s) {
    try {
        // Create MD5 Hash
        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        digest.update(s.getBytes());
        byte messageDigest[] = digest.digest();

        // Create Hex String
        StringBuffer hexString = new StringBuffer();
        for (int i=0; i<messageDigest.length; i++)
            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
        return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
    }
    return "";
  }
}