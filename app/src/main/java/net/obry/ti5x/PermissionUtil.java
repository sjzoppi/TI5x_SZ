/*
    Support routines for the API Level 23 and above requirements for
    discretionary/dynamic permissions.

    Copyright 2017       Steven Zoppi <about-ti5x@zoppi.org>.

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.obry.ti5x;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;

/**
 * Utility class that wraps access to the runtime permissions API in M and provides basic helper
 * methods.
 */
public abstract class PermissionUtil
  {

    /**
     * Check that all given permissions have been granted by verifying that each entry in the
     * given array is of the value {@link PackageManager#PERMISSION_GRANTED}.
     *
     * @see Activity#onRequestPermissionsResult(int, String[], int[])
     */
    public static boolean verifyPermissions(int[] grantResults)
      {
        // At least one result must be checked.
        if ( grantResults.length < 1 )
          {
            return false;
          }

        // Verify that each required permission has been granted, otherwise return false.
        for ( int result : grantResults )
          {
            if ( result != PackageManager.PERMISSION_GRANTED )
              {
                return false;
              }
          }
        return true;
      }
    /**
     * Just a check to see if we have marshmallow (version 23)
     *
     * @return
     */
    public static boolean shouldAskPermission()
      {

        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);

      }

    public static boolean hasCorrectPermission(Context ctx)
      {
        return (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
                &&
                (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED);
      }


  }
