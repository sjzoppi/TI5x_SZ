/*
    ti5x calculator emulator -- mainline

    Copyright 2011-2012 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015-2018 Pascal Obry <pascal@obry.net>.

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
import android.app.Notification;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class Main extends android.app.Activity
  {
    android.text.ClipboardManager                    Clipboard;
    android.app.NotificationManager                  Notiman;
    java.util.Map< android.view.MenuItem, Runnable > OptionsMenu;
    java.util.Map< android.view.MenuItem, Runnable > ContextMenu;

    interface RequestResponseAction
      {
        /* response to an activity result */
        void Run
        (
            int ResultCode,
            Intent Data
        );
      }

    android.util.SparseArray< RequestResponseAction > ActivityResultActions;

    /* request codes, all arbitrarily assigned */
    static final int LoadProgramRequest = 1;
    static final int ImportDataRequest  = 2;
    static final int SaveProgramRequest = 3;
    static final int ExportDataRequest  = 4;

    static final int SwitchSaveAs = android.app.Activity.RESULT_FIRST_USER;
    static final int SwitchAppend = android.app.Activity.RESULT_FIRST_USER + 1;

    // Response Results for Permissions Interactions

    private final static int READ_WRITE_EXTERNAL_RESULT = 100;
    private final static int CHOOSE_FILE_RESULT         = 102;

    boolean ExportAppend, ExportNumbersOnly;

    ViewGroup PickerExtra, SaveAsExtra;
    View mLayout;

    boolean ShuttingDown = false;
    boolean StateLoaded  = false; /* will be reset to false every time activity is killed and restarted */

    static final int NotifyProgramDone = 1; /* arbitrary notification ID */

    // All built-in libraries described here

    static final int BUILTIN_MASTER_LIBRARY_INDEX = 0; // index in the BuiltinLibraries array

    public static final BuiltinLibrary[] BuiltinLibraries =
        {
            new BuiltinLibrary( R.string.master_library, R.raw.ml ),
            new BuiltinLibrary( R.string.appstats_library, R.raw.st ),
            new BuiltinLibrary( R.string.realestate_library, R.raw.re ),
            new BuiltinLibrary( R.string.surveying_library, R.raw.sy ),
            new BuiltinLibrary( R.string.aviation_library, R.raw.av ),
            new BuiltinLibrary( R.string.marine_navigation_library, R.raw.ng ),
            new BuiltinLibrary( R.string.leisure_library, R.raw.le ),
            new BuiltinLibrary( R.string.securities_library, R.raw.sa ),
            new BuiltinLibrary( R.string.businessdecisions_library, R.raw.bd ),
            new BuiltinLibrary( R.string.mathutil_library, R.raw.mu ),
            new BuiltinLibrary( R.string.electrical_library, R.raw.ee ),
            new BuiltinLibrary( R.string.contribution_library, R.raw.ct )
        };

    private static String[] getBuiltinLibraries( android.content.Context ctx )
      {
        String[] result = new String[ BuiltinLibraries.length ];

        for ( int i = 0 ; i < BuiltinLibraries.length ; i++ )
          result[ i ] = BuiltinLibraries[ i ].getName( ctx );
        return result;
      }

    public static final BuiltinLibrary[] BuiltinPrograms =
        {
              new BuiltinLibrary(R.string.input_code, R.raw.ee19_input_code),
              new BuiltinLibrary(R.string.construct_nam_code, R.raw.ee19_construct_nam_code)
        };

    private static String[] getBuiltinPrograms( android.content.Context ctx )
      {
        String[] result = new String[ BuiltinPrograms.length ];

        for ( int i = 0 ; i < BuiltinPrograms.length ; i++ )
          result[ i ] = BuiltinPrograms[ i ].getName( ctx );
        return result;
      }

    public void ShowHelp
        (
            String Path,
            String[] FormatArgs
        )
      {
    /* launches the Help activity, displaying the page in my resources with
       the specified Path. */
        final Intent LaunchHelp = new Intent( Intent.ACTION_VIEW )
            .addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP );
    /* must always load the page contents, can no longer pass a file:///android_asset/
       URL with Android 4.0. */
        byte[] HelpRaw;
        {
          java.io.InputStream ReadHelp;
          try
            {
              ReadHelp = getAssets().open( Path );
              HelpRaw = Persistent.ReadAll( ReadHelp );
            }
          catch ( java.io.IOException Failed )
            {
              throw new RuntimeException( "can't read help page: " + Failed );
            }
          try
            {
              ReadHelp.close();
            }
          catch ( java.io.IOException WhoCares )
            {
              /* I mean, really? */
            }
        }
        LaunchHelp.putExtra
            (
                Help.ContentID,
                FormatArgs != null
                ?
                String.format( Global.StdLocale, new String( HelpRaw ), ( Object[] ) FormatArgs )
                    .getBytes()
                :
                HelpRaw
            );
        LaunchHelp.setClass( this, Help.class );
        startActivity( LaunchHelp );
      }

    class ReplaceConfirm
        extends android.app.AlertDialog
        implements DialogInterface.OnClickListener
      {
        final Runnable LaunchWhat;

        ReplaceConfirm
            (
                android.content.Context ctx,
                int MsgID,
                Runnable LaunchWhat
            )
          {
            super( ctx );
            this.LaunchWhat = LaunchWhat;
            setMessage( ctx.getString( MsgID ) );
            setButton
                (
                    DialogInterface.BUTTON_POSITIVE,
                    ctx.getString( R.string.replace ),
                    this
                );
            setButton
                (
                    DialogInterface.BUTTON_NEGATIVE,
                    ctx.getString( R.string.cancel ),
                    this
                );
          }

        @Override
        public void onClick
            (
                DialogInterface TheDialog,
                int WhichButton
            )
          {
            if ( WhichButton == DialogInterface.BUTTON_POSITIVE )
              {
                LaunchWhat.run();
              }
            dismiss();
          }
      }

    class FeedbackDialog
        extends android.app.Dialog
        implements DialogInterface.OnDismissListener
      {
        final android.content.Context ctx;
        android.widget.RadioGroup TheButtons;

        FeedbackDialog
            (
                android.content.Context ctx
            )
          {
            super( ctx );
            this.ctx = ctx;
          }

        @Override
        public void onCreate
            (
                android.os.Bundle savedInstanceState
            )
          {
            setTitle( R.string.button_feedback );
            final android.widget.LinearLayout MainLayout = new android.widget.LinearLayout( ctx );
            MainLayout.setOrientation( android.widget.LinearLayout.VERTICAL );
            setContentView( MainLayout );
            TheButtons = new android.widget.RadioGroup( ctx );
            final ViewGroup.LayoutParams ButtonLayout =
                new ViewGroup.LayoutParams
                    (
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            {
              final RadioButton FeedbackClick = new RadioButton( ctx );
              FeedbackClick.setText( R.string.feedback_click );
              FeedbackClick.setId( ButtonGrid.FEEDBACK_CLICK );

              final RadioButton FeedbackVibrate = new RadioButton( ctx );
              FeedbackVibrate.setText( R.string.feedback_vibrate );
              FeedbackVibrate.setId( ButtonGrid.FEEDBACK_VIBRATE );

              final RadioButton FeedbackBoth = new RadioButton( ctx );
              FeedbackBoth.setText( R.string.feedback_click_and_vibrate );
              FeedbackBoth.setId( ButtonGrid.FEEDBACK_BOTH );

              final RadioButton FeedbackNone = new RadioButton( ctx );
              FeedbackNone.setText( R.string.feedback_none );
              FeedbackNone.setId( ButtonGrid.FEEDBACK_NONE );

              TheButtons.addView( FeedbackClick, 0, ButtonLayout );
              TheButtons.addView( FeedbackVibrate, 1, ButtonLayout );
              TheButtons.addView( FeedbackBoth, 2, ButtonLayout );
              TheButtons.addView( FeedbackNone, 3, ButtonLayout );
            }
            MainLayout.addView( TheButtons, ButtonLayout );
            TheButtons.check( Global.Buttons.FeedbackType );
            setOnDismissListener( this );
          }

        @Override
        public void onDismiss
            (
                DialogInterface TheDialog
            )
          {
            Global.Buttons.SetFeedbackType( TheButtons.getCheckedRadioButtonId() );
          }
      }

    void LaunchImportPicker()
      {
        final Picker.PickerAltList[] AltLists =
            {
                new Picker.PickerAltList
                    (
                        R.id.select_likely_files,
                        getString( R.string.import_prompt ),
                        getString( R.string.no_data_files ),
                        Persistent.LikelyDataExts,
                        null
                    ),
                new Picker.PickerAltList
                    (
                        R.id.select_all_files,
                        getString( R.string.import_prompt ),
                        getString( R.string.no_data_files ),
                        null,
                        null
                    ),
            };
        PickerExtra = ( ViewGroup ) getLayoutInflater().inflate( R.layout.import_type, null );
        Picker.Launch
            (
                Main.this,
                getString( R.string.import_ ),
                ImportDataRequest,
                PickerExtra,
                Persistent.ExternalDataDirectories,
                AltLists
            );
      }

    void LaunchExportPicker()
      {
        final android.view.LayoutInflater Inflater    = getLayoutInflater();
        final ViewGroup[]                 ExportExtra = new ViewGroup[ 2 ];
        /* need two copies */
        for ( int i = 0 ; i < 2 ; ++i )
          {
            ExportExtra[ i ] = ( ViewGroup ) Inflater.inflate( R.layout.export_type, null );
            final RadioButton NumbersOnly =
                ( RadioButton ) ExportExtra[ i ].findViewById( R.id.select_numbers_only );
            final RadioButton AllPrintout =
                ( RadioButton ) ExportExtra[ i ].findViewById( R.id.select_all_printout );
            NumbersOnly.setChecked( ExportNumbersOnly );
            AllPrintout.setChecked( !ExportNumbersOnly );
            NumbersOnly.setOnClickListener
                (
                    new View.OnClickListener()
                      {
                        public void onClick
                            (
                                View TheView
                            )
                          {
                            ExportNumbersOnly = true;
                          } /*onClick*/
                      }
                );
            AllPrintout.setOnClickListener
                (
                    new View.OnClickListener()
                      {
                        public void onClick
                            (
                                View TheView
                            )
                          {
                            ExportNumbersOnly = false;
                          } /*onClick*/
                      }
                );
          }
        SaveAsExtra = ( ViewGroup ) Inflater.inflate( R.layout.save_append, null );
        SaveAsExtra.addView
            (
                ExportExtra[ 0 ],
                new ViewGroup.LayoutParams
                    (
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
        SaveAsExtra.findViewById( R.id.switch_append ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View TheView
                        )
                      {
                        SaveAs.Current.setResult( SwitchAppend );
                        SaveAs.Current.finish();
                      } /*onClick*/
                  }
            );
        PickerExtra = ( ViewGroup ) Inflater.inflate( R.layout.save_new, null );
        PickerExtra.addView
            (
                ExportExtra[ 1 ],
                new ViewGroup.LayoutParams
                    (
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
        PickerExtra.findViewById( R.id.switch_new ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View TheView
                        )
                      {
                        Picker.Current.setResult( SwitchSaveAs );
                        Picker.Current.finish();
                      } /*onClick*/
                  }
            );
        if ( ExportAppend )
          {
            ExportExtra[ 1 ].addView
                (
                    getLayoutInflater().inflate( R.layout.import_type, null ),
                    new ViewGroup.LayoutParams
                        (
                            ViewGroup.LayoutParams.FILL_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
            final Picker.PickerAltList[] AltLists =
                {
                    new Picker.PickerAltList
                        (
                            R.id.select_likely_files,
                            getString( R.string.export_prompt ),
                            getString( R.string.no_data_files ),
                            Persistent.LikelyDataExts,
                            null
                        ),
                    new Picker.PickerAltList
                        (
                            R.id.select_all_files,
                            getString( R.string.export_prompt ),
                            getString( R.string.no_data_files ),
                            null,
                            null
                        ),
                };
            Picker.Launch
                (
                    Main.this,
                    getString( R.string.append ),
                    ExportDataRequest,
                    PickerExtra,
                    Persistent.ExternalDataDirectories,
                    AltLists
                );
          }
        else
          {
            SaveAs.Launch
                (
                    Main.this,
                    ExportDataRequest,
                    getString( R.string.exported_data ),
                    Persistent.DataDir,
                    SaveAsExtra,
                    ""
                );
          }
      }

    @Override
    public boolean onCreateOptionsMenu
        (
            android.view.Menu TheMenu
        )
      {
        OptionsMenu = new java.util.HashMap< android.view.MenuItem, Runnable >();
        android.view.MenuItem ThisItem;
        OptionsMenu.put
            (
                TheMenu.add( R.string.show_calc_help ),
                new Runnable()
                  {
                    public void run()
                      {
                        ShowHelp( "help/index.html", null );
                      } /*run*/
                  }
            );
        ThisItem = TheMenu.add( R.string.show_overlay );
        OptionsMenu.put
            (
                ThisItem,
                new Runnable()
                  {
                    public void run()
                      {
                        Global.Buttons.OverlayVisible = !Global.Buttons.OverlayVisible;
                        Global.Buttons.invalidate();
                        /* ToggleOverlayItem.setChecked(Global.Buttons.OverlayVisible); */
                        /* apparently can't do this in initial part of options menu */
                      } /*run*/
                  }
            );
        /* ThisItem.setCheckable(true); */
        /* apparently can't do this in initial part of options menu */
        OptionsMenu.put
            (
                TheMenu.add( R.string.show_module_help ),
                new Runnable()
                  {
                    public void run()
                      {
                        if ( Global.Calc != null && Global.Calc.ModuleHelp != null )
                          {
                            final Intent ShowHelp = new Intent( Intent.ACTION_VIEW );
                            ShowHelp.putExtra( Help.ContentID, Global.Calc.ModuleHelp );
                            ShowHelp.setClass( Main.this, Help.class );
                            startActivity( ShowHelp );
                          }
                        else
                          {
                            Toast.makeText
                                (
                                    Main.this,
                                    getString( R.string.no_module_help ),
                                    Toast.LENGTH_SHORT
                                ).show();
                          }
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.show_printer ),
                new Runnable()
                  {
                    public void run()
                      {
                        startActivity
                            (
                                new Intent( Intent.ACTION_VIEW )
                                    .setClass( Main.this, PrinterView.class )
                            );
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.load_prog ),
                new Runnable()
                  {
                    public void run()
                      {
                        final Picker.PickerAltList[] AltLists =
                            {
                                /* note the code that responds to the result Intent assumes
                                   that element 0 is saved programs and element 1 is libraries */
                                new Picker.PickerAltList
                                    (
                                        R.id.select_saved,
                                        getString( R.string.prog_prompt ),
                                        getString( R.string.no_programs ),
                                        new String[] { Persistent.ProgExt },
                                        getBuiltinPrograms( Main.this )
                                    ),
                                new Picker.PickerAltList
                                    (
                                        R.id.select_libraries,
                                        getString( R.string.module_prompt ),
                                        getString( R.string.no_modules ),
                                        new String[] { Persistent.LibExt },
                                        getBuiltinLibraries( Main.this )
                                        /* item representing selection of built-in libraries */
                                    ),
                            };
                        PickerExtra = ( ViewGroup ) getLayoutInflater().inflate(
                            R.layout.prog_type, null );

                        // check if directories exists to avoid messages about unreadable location

                        ArrayList< String > CalcDirs = new ArrayList< String >();

                        final String ProgramsDir =
                            android.os.Environment.getExternalStorageDirectory()
                                .getAbsolutePath() + "/" + Persistent.ProgramsDir;

                        final String DataDir =
                            android.os.Environment.getExternalStorageDirectory()
                                .getAbsolutePath() + "/" + Persistent.DataDir;

                        if ( new java.io.File( ProgramsDir ).exists() )
                          {
                            CalcDirs.add( Persistent.ProgramsDir );
                          }
                        if ( new java.io.File( DataDir ).exists() )
                          {
                            CalcDirs.add( Persistent.DataDir );
                          }

                        Picker.Launch
                            (
                                Main.this,
                                getString( R.string.load ),
                                LoadProgramRequest,
                                PickerExtra,
                                CalcDirs.toArray( new String[ 0 ] ),
                                AltLists
                            );
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.opt_feedback ),
                new Runnable()
                  {
                    public void run()
                      {
                        new FeedbackDialog( Main.this ).show();
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.save_program_as ),
                new Runnable()
                  {
                    public void run()
                      {
                        SaveAs.Launch
                            (
                                Main.this,
                                SaveProgramRequest,
                                getString( R.string.program ),
                                Persistent.ProgramsDir,
                                null,
                                Persistent.ProgExt
                            );
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.import_data ),
                new Runnable()
                  {
                    public void run()
                      {
                        if ( !Global.Calc.ImportInProgress() )
                          {
                            LaunchImportPicker();
                          }
                        else
                          {
                            new ReplaceConfirm
                                (
                                    Main.this,
                                    R.string.query_replace_import,
                                    new Runnable()
                                      {
                                        public void run()
                                          {
                                            LaunchImportPicker();
                                          }
                                      }
                                ).show();
                          }
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.export_data ),
                new Runnable()
                  {
                    public void run()
                      {
                        final Runnable DoIt =
                            new Runnable()
                              {
                                public void run()
                                  {
                                    ExportAppend = false;
                                    ExportNumbersOnly = true;
                                    LaunchExportPicker();
                                  } /*run*/
                              };
                        if ( !Global.Export.IsOpen() )
                          {
                            DoIt.run();
                          }
                        else
                          {
                            new ReplaceConfirm
                                (
                                    Main.this,
                                    R.string.query_replace_export,
                                    DoIt
                                ).show();
                          }
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.about_me ),
                new Runnable()
                  {
                    public void run()
                      {
                        String VersionName;
                        try
                          {
                            VersionName =
                                getPackageManager().getPackageInfo(
                                    getPackageName(), 0 ).versionName;
                          }
                        catch ( android.content.pm.PackageManager.NameNotFoundException CantFindMe )
                          {
                            VersionName = "Xperimental";
                          }
                        Date     today = new Date();
                        Calendar cal   = Calendar.getInstance();
                        cal.setTime( today );
                        String YearStr = String.valueOf( cal.get( Calendar.YEAR ) );
                        ShowHelp(
                            "help/about.html",
                            new String[] { VersionName, YearStr, YearStr }
                        );
                      } /*run*/
                  }
            );
        OptionsMenu.put
            (
                TheMenu.add( R.string.turn_off ),
                new Runnable()
                  {
                    public void run()
                      {
                        ShuttingDown = true; /* don't save any state */
                        Persistent.ResetState( Main.this );
                        finish(); /* start afresh next time */
                      } /*run*/
                  }
            );
        return true;
      }

    @Override
    public void onCreateContextMenu
        (
            android.view.ContextMenu TheMenu,
            View TheView,
            android.view.ContextMenu.ContextMenuInfo TheMenuInfo
        )
      {
        if ( !Global.BGTaskInProgress() && !Global.Calc.ProgMode && !Global.Calc.TaskRunning )
          {
            ContextMenu = new java.util.HashMap< android.view.MenuItem, Runnable >();
            ContextMenu.put
                (
                    TheMenu.add( R.string.copy_number ),
                    new Runnable()
                      {
                        public void run()
                          {
                            if ( Global.Calc.CurDisplay != null )
                              {
                                String NumString = Global.Calc.CurDisplay;
                                if ( NumString.length() > 3 )
                                  {
                                    /* put in explicit "e" like most other software expects */
                                    if ( NumString.charAt( NumString.length() - 3 ) == ' ' )
                                      {
                                        NumString =
                                            NumString.substring( 0, NumString.length() - 3 )
                                                +
                                                "e+"
                                                +
                                                NumString.substring( NumString.length() - 2 );
                                        /* leave off the space */
                                      }
                                    else if ( NumString.charAt( NumString.length() - 3 ) == '-' )
                                      {
                                        NumString =
                                            NumString.substring( 0, NumString.length() - 3 )
                                                +
                                                "e"
                                                +
                                                NumString.substring( NumString.length() - 3 );
                                      }
                                  }
                                Clipboard.setText( NumString );
                              }
                          } /*run*/
                      }
                );
            ContextMenu.put
                (
                    TheMenu.add( R.string.copy_full_number ),
                    new Runnable()
                      {
                        public void run()
                          {
                            if ( Global.Calc.CurDisplay != null )
                              {
                                Clipboard.setText
                                    (
                                        Global.Calc.X.formatString(
                                            Global.StdLocale, Global.NrSigFigures )
                                    );
                              }
                          } /*run*/
                      }
                );
            ContextMenu.put
                (
                    TheMenu.add( R.string.paste_number ),
                    new Runnable()
                      {
                        public void run()
                          {
                            boolean OK = false;
                            do /*once*/
                              {
                                Number X = new Number();
                                String NumString;
                                {
                                  final CharSequence NumChars = Clipboard.getText();
                                  if ( NumChars == null )
                                    break;
                                  NumString = NumChars.toString();
                                }
                                for ( boolean TriedMassage = false ; ; )
                                  {
                                    try
                                      {
                                        X = new Number( NumString );
                                        OK = true;
                                        break;
                                      }
                                    catch ( NumberFormatException BadNum )
                                      {
                                        if ( !TriedMassage )
                                          {
                                            if
                                                (
                                                NumString.length() > 3
                                                    &&
                                                    (
                                                        NumString.charAt(
                                                            NumString.length() - 3 ) == '-'
                                                            ||
                                                            NumString.charAt(
                                                                NumString.length() - 3 ) == ' '
                                                    )
                                                    &&
                                                    NumString.charAt(
                                                        NumString.length() - 4 ) != 'e'
                                                    &&
                                                    NumString.charAt(
                                                        NumString.length() - 4 ) != 'E'
                                                )
                                              {
                                                NumString =
                                                    NumString.substring( 0, NumString.length() - 3 )
                                                        +
                                                        "e"
                                                        +
                                                        NumString.substring
                                                            (
                                                                NumString.length()
                                                                    -
                                                                    (
                                                                        NumString.charAt(
                                                                            NumString.length() - 3 )
                                                                            ==
                                                                            ' '
                                                                        ?
                                                                        2 /* leave off the space */
                                                                        :
                                                                        3 /* include the minus sign */
                                                                    )
                                                            );
                                              }
                                          }
                                      }
                                    if ( TriedMassage )
                                      break;
                                    TriedMassage = true;
                                  }
                                if ( !OK )
                                  break;
                                Global.Calc.SetX( X, false );
                              }
                            while ( false );
                            if ( !OK )
                              {
                                Toast.makeText
                                    (
                                        Main.this,
                                        getString( R.string.paste_nan ),
                                        Toast.LENGTH_SHORT
                                    ).show();
                              }
                          } /*run*/
                      }
                );
          }
        else
          {
            ContextMenu = null;
          }
      }

    void BuildActivityResultActions()
      {
        ActivityResultActions = new android.util.SparseArray< RequestResponseAction >();
        ActivityResultActions.put
            (
                LoadProgramRequest,
                new RequestResponseAction()
                  {
                    public void Run
                        (
                            int ResultCode,
                            Intent Data
                        )
                      {
                        final String  ProgName       = Data.getData().getPath();
                        final int     SelId          = Data.getIntExtra( Picker.SpeIndexID, 0 );
                        final int     FirstBuiltinId = Data.getIntExtra( Picker.BuiltinIndexID, 0 );
                        final boolean IsLib          = Data.getIntExtra(
                            Picker.AltIndexID, 0 ) != 0;
              /* assumes AltLists array passed to Picker has element 0 for
                 saved programs and element 1 for libraries */
                        final boolean LoadingBuiltinLibrary = IsLib && ProgName.intern().equals(
                            "/" );
              /* It appears onActivityResult is liable to be called before
                 onResume. Therefore I do additional restoring/saving state
                 here to ensure the saved state includes the newly-loaded
                 program/library. */
                        class LoadProgram extends Global.Task
                          {
                            private static final int LOAD_STATE           = 0;
                            private static final int LOAD_BUILTIN_LIBRARY = 1;
                            private static final int LOAD_PROG            = 2;
                            private static final int LOAD_DONE            = 3;
                            private int         Step;
                            private Global.Task Subtask;

                            private LoadProgram
                                (
                                    int Step
                                )
                              {
                                this.Step = Step;
                                Subtask = null;
                              }

                            private LoadProgram()
                              {
                                this
                                    (
                                        StateLoaded
                                        ?
                                        LoadingBuiltinLibrary
                                        ? LOAD_BUILTIN_LIBRARY
                                        : LOAD_PROG
                                        :
                                        LOAD_STATE
                                    );
                              }

                            @Override
                            public boolean PreRun()
                              {
                                switch ( Step )
                                  {
                                    case LOAD_STATE:
                                      Subtask = new Persistent.RestoreState( Main.this );
                                      /* if not already done */
                                      break;
                                    case LOAD_BUILTIN_LIBRARY:
                                      Subtask = new Persistent.LoadBuiltin(
                                          Main.this, true, SelId );
                                      break;
                                    case LOAD_PROG:
                                      if ( SelId >= FirstBuiltinId )
                                        {
                                          // a built-in programs
                                          Subtask = new Persistent.LoadBuiltin(
                                              Main.this, false, SelId - FirstBuiltinId );
                                        }
                                      else
                                        {
                                          Subtask = new Persistent.Load
                                              (
                                                  ProgName,
                                                  IsLib,
                                                  false,
                                                  Global.Disp,
                                                  Global.Buttons,
                                                  Global.Calc
                                              );
                                        }
                                      break;
                                  }
                                return Subtask != null && Subtask.PreRun();
                              }

                            @Override
                            public void BGRun()
                              {
                                if ( Subtask != null )
                                  {
                                    if ( Step == LOAD_STATE )
                                      {
                                        Subtask.BGRun();
                                      }
                                    else
                                      {
                                        try
                                          {
                                            Subtask.BGRun();
                                            if ( Subtask.TaskFailure != null )
                                              {
                                                SetStatus( -1, Subtask.TaskFailure );
                                              }
                                          }
                                        catch ( Persistent.DataFormatException Failed )
                                          {
                                            SetStatus( -1, Failed );
                                          }
                                      }
                                  }
                              }

                            @Override
                            public void PostRun()
                              {
                                if ( Subtask != null )
                                  {
                                    Subtask.PostRun();
                                    switch ( Step )
                                      {
                                        case LOAD_STATE:
                                          StateLoaded = true;
                                          Global.StartBGTask
                                              (
                                                  new LoadProgram
                                                      (
                                                          LoadingBuiltinLibrary
                                                          ?
                                                          LOAD_BUILTIN_LIBRARY
                                                          :
                                                          LOAD_PROG
                                                      ),
                                                  null
                                              );
                                          break;
                                        case LOAD_BUILTIN_LIBRARY:
                                        case LOAD_PROG:
                                          if ( TaskFailure == null )
                                            {
                                              Toast.makeText
                                                  (
                                                      Main.this,
                                                      String.format
                                                          (
                                                              Global.StdLocale,
                                                              getString
                                                                  (
                                                                      IsLib
                                                                      ?
                                                                      R.string.library_loaded
                                                                      :
                                                                      R.string.program_loaded
                                                                  ),
                                                              LoadingBuiltinLibrary
                                                              ?
                                                              BuiltinLibraries[ SelId ].getName(
                                                                  Main.this )
                                                              :
                                                              new java.io.File( ProgName ).getName()
                                                          ),
                                                      Toast.LENGTH_SHORT
                                                  ).show();
                                              Global.StartBGTask
                                                  (
                                                      new Global.Task()
                                                        {
                                                          @Override
                                                          public void BGRun()
                                                            {
                                                              Persistent.SaveState(
                                                                  Main.this, IsLib );
                                                            } /*BGRun*/
                                                        } /*Task*/,
                                                      getString( R.string.saving )
                                                  );
                                            }
                                          else
                                            {
                                              Toast.makeText
                                                  (
                                                      Main.this,
                                                      String.format
                                                          (
                                                              Global.StdLocale,
                                                              getString( R.string.file_load_error ),
                                                              TaskFailure.toString()
                                                          ),
                                                      Toast.LENGTH_LONG
                                                  ).show();
                                            }
                                          if ( Step == LOAD_PROG )
                                            {
                                              // we have loaded a new user's program, select it
                                              Global.Calc.SelectProgram( 0, false );
                                            }
                                          break;
                                      }
                                  }
                              }
                          }

                        Global.StartBGTask
                            (
                                new LoadProgram(),
                                getString( R.string.loading )
                            );
                      } /*Run*/
                  }
            );
        ActivityResultActions.put
            (
                SaveProgramRequest,
                new RequestResponseAction()
                  {
                    public void Run
                        (
                            int ResultCode,
                            Intent Data
                        )
                      {
                        final String TheName =
                            Data.getData().getPath().substring( 1 ) /* ignoring leading slash */
                                + Persistent.ProgExt;
                        final String SaveDir =
                            android.os.Environment.getExternalStorageDirectory()
                                .getAbsolutePath() + "/" + Persistent.ProgramsDir;
                        Global.StartBGTask
                            (

                                new Global.Task()
                                  {
                                    @Override
                                    public void BGRun()
                                      {
                                        try
                                          {
                                            new java.io.File( SaveDir ).mkdirs();
                                            Persistent.Save
                                                (
                                                    Global.Buttons,
                                                    Global.Calc,
                                                    false,
                                                    false,
                                                    SaveDir + "/" + TheName
                                                );
                                          }
                                        catch ( RuntimeException Failed )
                                          {
                                            SetStatus( -1, Failed );
                                          }
                                      }

                                    @Override
                                    public void PostRun()
                                      {
                                        if ( TaskStatus == 0 )
                                          {
                                            Toast.makeText
                                                (
                                                    Main.this,
                                                    String.format
                                                        (
                                                            Global.StdLocale,
                                                            getString( R.string.program_saved ),
                                                            TheName
                                                        ),
                                                    Toast.LENGTH_SHORT
                                                ).show();
                                          }
                                        else
                                          {
                                            Toast.makeText
                                                (
                                                    Main.this,
                                                    String.format
                                                        (
                                                            Global.StdLocale,
                                                            getString(
                                                                R.string.program_save_error ),
                                                            TaskFailure.toString()
                                                        ),
                                                    Toast.LENGTH_LONG
                                                ).show();
                                          }
                                      }
                                  },
                                getString( R.string.saving )
                            );
                      } /*Run*/
                  }
            );
        ActivityResultActions.put
            (
                ImportDataRequest,
                new RequestResponseAction()
                  {
                    public void Run
                        (
                            int ResultCode,
                            Intent Data
                        )
                      {
                        final String FileName = Data.getData().getPath();
                        try
                          {
                            Global.Calc.ClearImport();
                            Global.Import.ImportData( FileName );
                            Toast.makeText
                                (
                                    Main.this,
                                    String.format
                                        (
                                            Global.StdLocale,
                                            getString( R.string.import_started ),
                                            FileName
                                        ),
                                    Toast.LENGTH_SHORT
                                ).show();

                          }
                        catch ( Persistent.DataFormatException Failed )
                          {
                            Toast.makeText
                                (
                                    Main.this,
                                    String.format
                                        (
                                            Global.StdLocale,
                                            getString( R.string.file_load_error ),
                                            Failed.toString()
                                        ),
                                    Toast.LENGTH_LONG
                                ).show();
                          }
                      } /*Run*/
                  }
            );
        ActivityResultActions.put
            (
                ExportDataRequest,
                new RequestResponseAction()
                  {
                    public void Run
                        (
                            int ResultCode,
                            Intent Data
                        )
                      {
                        switch ( ResultCode )
                          {
                            case android.app.Activity.RESULT_OK:
                              Global.Export.Close();
                              try
                                {
                                  String FileName = Data.getData().getPath();
                                  if ( !ExportAppend )
                                    {
                                      final String SaveDir =
                                          android.os.Environment.getExternalStorageDirectory()
                                              .getAbsolutePath()
                                              +
                                              "/"
                                              +
                                              Persistent.DataDir;
                                      new java.io.File( SaveDir ).mkdirs();
                                      FileName = SaveDir + FileName;
                                      /* note FileName will have leading slash */
                                    }
                                  Global.Export.Open( FileName, ExportAppend, ExportNumbersOnly );
                                  Toast.makeText
                                      (
                                          Main.this,
                                          String.format
                                              (
                                                  Global.StdLocale,
                                                  getString( R.string.export_started ),
                                                  FileName
                                              ),
                                          Toast.LENGTH_SHORT
                                      ).show();
                                }
                              catch ( RuntimeException Failed )
                                {
                                  Toast.makeText
                                      (
                                          Main.this,
                                          String.format
                                              (
                                                  Global.StdLocale,
                                                  getString( R.string.export_error ),
                                                  Failed.toString()
                                              ),
                                          Toast.LENGTH_LONG
                                      ).show();
                                }
                              break;
                            case SwitchAppend:
                            case SwitchSaveAs:
                              ExportAppend = ResultCode == SwitchAppend;
                              LaunchExportPicker();
                              break;
                          }
                      } /*Run*/
                  }
            );
      }

    @Override
    public boolean onOptionsItemSelected
        (
            android.view.MenuItem TheItem
        )
      {
        boolean        Handled = false;
        final Runnable Action  = OptionsMenu.get( TheItem );
        if ( Action != null )
          {
            Action.run();
            Handled = true;
          }
        return Handled;
      }

    @Override
    public boolean onContextItemSelected
        (
            android.view.MenuItem TheItem
        )
      {
        boolean        Handled = false;
        final Runnable Action  = ContextMenu.get( TheItem );
        if ( Action != null )
          {
            Action.run();
            Handled = true;
          }
        return Handled;
      }

    @Override
    public void onActivityResult
        (
            int RequestCode,
            int ResultCode,
            Intent Data
        )
      {
        Picker.Cleanup();
        PickerExtra = null;
        SaveAs.Cleanup();
        SaveAsExtra = null;
        if ( ResultCode != android.app.Activity.RESULT_CANCELED )
          {
            final RequestResponseAction Action = ActivityResultActions.get( RequestCode );
            if ( Action != null )
              {
                Action.Run( ResultCode, Data );
              }
          }
      }

    void CheckDisplayOrientation()
      {
    /* ensures that landscape orientation is only allowed on screens that
       are tall enough. */
        final android.view.Display        MainDisplay = getWindowManager().getDefaultDisplay();
        final android.util.DisplayMetrics MainMetrics = new android.util.DisplayMetrics();
        MainDisplay.getMetrics( MainMetrics );
        if ( MainMetrics.heightPixels / MainMetrics.densityDpi * 160.0f <= 640.0f )
          {
      /* Lock to portrait orientation on phone-sized screens. Note once I do this,
         I stop getting further notifications of orientation change. */
            setRequestedOrientation( android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT );
          }
      }

    void PostNotification
        (
            int MsgID,
            boolean Ongoing
        )
      {
        final android.app.Notification NotifyDone = new android.app.Notification
            (
                R.drawable.icon,
                getString( R.string.app_name ),
                System.currentTimeMillis()
            );
        NotifyDone.contentView = new android.widget.RemoteViews
            (
                "net.obry.ti5x",
                R.layout.prog_status
            );
        NotifyDone.contentView.setTextViewText( R.id.notify_prog_status, getString( MsgID ) );
        NotifyDone.contentIntent = android.app.PendingIntent.getActivity
            (
                Main.this,
                0,
                new Intent()
                    .addFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP )
                    .setClass( Main.this, Main.class ),
                0
            );
        if ( Ongoing )
          {
            NotifyDone.flags = Notification.FLAG_ONGOING_EVENT;
          }
        else
          {
            NotifyDone.flags = Notification.FLAG_AUTO_CANCEL;
          }
        Notiman.notify( NotifyProgramDone, NotifyDone );
      }

    @Override
    public void onCreate
        (
            android.os.Bundle savedInstanceState
        )
      {
        super.onCreate( savedInstanceState );
        getWindow().requestFeature( android.view.Window.FEATURE_CUSTOM_TITLE );
        setContentView( R.layout.main );
        Global.Disp = ( Display ) findViewById( R.id.display );
        Global.Label = ( LabelCard ) findViewById( R.id.help_card );
        Global.Buttons = ( ButtonGrid ) findViewById( R.id.buttons );
        Global.ProgressWidgets = findViewById( R.id.progress );
        Global.ProgressMessage =
            ( android.widget.TextView ) Global.ProgressWidgets.findViewById(
                R.id.progress_message );
        Global.UIRun = new android.os.Handler();
        Global.Print = new Printer( this );
        Global.Calc = new State( this );
        Global.Import = new Importer();
        Global.Export = new Exporter( this );
        Global.Test = new Tester();
        BuildActivityResultActions();
        Clipboard = ( android.text.ClipboardManager ) getSystemService( CLIPBOARD_SERVICE );
        registerForContextMenu( Global.Disp );
        Notiman = ( android.app.NotificationManager ) getSystemService( NOTIFICATION_SERVICE );

        /*
         * check to see if we should ask for permissions before we do anything else
         */
        mLayout = findViewById( R.id.keyboard_main_layout );

        if ( !PermissionUtil.hasCorrectPermission( this ) )
          {
            // Storage READ permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(
                this,
                new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                READ_WRITE_EXTERNAL_RESULT
            );
          }

        Global.Calc.OnStop = new Runnable()
          {
            public void run()
              {
                if ( !hasWindowFocus() )
                  {
                    PostNotification( R.string.prog_done, false );
                  }
              }
          };
        CheckDisplayOrientation();
      }

    @Override
    public void onPostCreate
        (
            android.os.Bundle SavedInstanceState
        )
      {
        super.onPostCreate( SavedInstanceState );
        getWindow().setFeatureInt
            (
                android.view.Window.FEATURE_CUSTOM_TITLE,
                R.layout.title_bar
            );
        findViewById( R.id.action_help ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View ButtonView
                        )
                      {
                        ShowHelp( "help/index.html", null );
                      } /*onClick*/
                  }
            );
        findViewById( R.id.action_helpmod ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View ButtonView
                        )
                      {
                        if ( Global.Calc != null && Global.Calc.ModuleHelp != null )
                          {
                            final Intent ShowHelp = new Intent( Intent.ACTION_VIEW );
                            ShowHelp.putExtra( Help.ContentID, Global.Calc.ModuleHelp );
                            ShowHelp.setClass( Main.this, Help.class );
                            startActivity( ShowHelp );
                          }
                        else
                          {
                            Toast.makeText
                                (
                                    Main.this,
                                    getString( R.string.no_module_help ),
                                    Toast.LENGTH_SHORT
                                ).show();
                          }
                      } /*onClick*/
                  }
            );
        findViewById( R.id.action_print ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View ButtonView
                        )
                      {
                        startActivity
                            (
                                new Intent( Intent.ACTION_VIEW )
                                    .setClass( Main.this, PrinterView.class )
                            );
                      } /*onClick*/
                  }
            );
        findViewById( R.id.action_menu ).setOnClickListener
            (
                new View.OnClickListener()
                  {
                    public void onClick
                        (
                            View ButtonView
                        )
                      {
                        openOptionsMenu();
                      } /*onClick*/
                  }
            );
      }

    @Override
    public void onDestroy()
      {
        Global.KillBGTask();
        if ( Global.Calc != null )
          {
            Global.Calc.ResetLibs();
      /* because new Global.Calc will be created in onCreate, so hopefully
         this will avoid "bitmap allocation exceeds budget" crashes */
          }
        super.onDestroy();
      }

    @Override
    public void onPause()
      {
        super.onPause();
        if ( !ShuttingDown )
          {
            Persistent.SaveState( this, false );
            /* don't bother making async, because I'm going to background anyway */
            if ( Global.Calc.TaskRunning )
              {
                PostNotification( R.string.prog_running, true );
              }
          }
      }

    @Override
    public void onResume()
      {
        super.onResume();
        Notiman.cancel( NotifyProgramDone );
        if ( !StateLoaded )
          {
            Global.StartBGTask
                (
                    new Persistent.RestoreState( Main.this )
                      {
                        @Override
                        public void PostRun()
                          {
                            super.PostRun();
                            StateLoaded = true;
                          }
                      },
                    getString( R.string.loading )
                );
          }
      }

    @Override
    public void onConfigurationChanged
        (
            android.content.res.Configuration NewConfig
        )
      {
        super.onConfigurationChanged( NewConfig );
        CheckDisplayOrientation();
      }

    @Override
    public boolean dispatchKeyEvent
        (
            android.view.KeyEvent TheEvent
        )
      {
        boolean Handled = false;
        if
            (
            TheEvent.getAction() == android.view.KeyEvent.ACTION_UP
                &&
                TheEvent.getKeyCode() == android.view.KeyEvent.KEYCODE_MENU
                &&
                Global.BGTaskInProgress()
            )
          {
            /* ignore attempt to bring up menu while save/load in progress */
            Handled = true;
          }
        if ( !Handled )
          {
            Handled = super.dispatchKeyEvent( TheEvent );
          }
        return Handled;
      }

    public void openFileUsingFM( String mimeType )
      {
        Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
        intent.setType( mimeType );
        intent.addCategory( Intent.CATEGORY_OPENABLE );

        // special intent for Samsung file manager
        Intent sIntent = new Intent( "com.sec.android.app.myfiles.PICK_DATA" );
        // if you want any file type, you can skip next line
        sIntent.putExtra( "CONTENT_TYPE", mimeType );
        sIntent.addCategory( Intent.CATEGORY_DEFAULT );

        Intent chooserIntent;
        if ( getPackageManager().resolveActivity( sIntent, 0 ) != null )
          {
            // it is device with samsung file manager
            chooserIntent = Intent.createChooser( sIntent, "Open file" );
            chooserIntent.putExtra( Intent.EXTRA_INITIAL_INTENTS, new Intent[] { intent } );
          }
        else
          {
            chooserIntent = Intent.createChooser( intent, "Open file" );
          }

        try
          {
            startActivityForResult( chooserIntent, CHOOSE_FILE_RESULT );
          }
        catch ( android.content.ActivityNotFoundException ex )
          {
            Toast.makeText
                (
                    getApplicationContext(),
                    "No suitable File Manager was found.",
                    Toast.LENGTH_SHORT
                ).show();
          }
      }
  }
