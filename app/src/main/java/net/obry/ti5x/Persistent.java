package net.obry.ti5x;
/*
    Saving/loading of programs, program libraries and calculator state

    Copyright 2011       Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015, 2016 Pascal Obry <pascal@obry.net>.
    Copyright 2016       Steven Zoppi <about-ti5x@zoppi.org>.


    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.zip.ZipEntry;

class ZipComponentWriter
  /* convenient writing of components to a ZIP archive, with automatic
    calculation of size and CRC fields. */
  {
    protected java.util.zip.ZipOutputStream Parent;
    protected ZipEntry Entry;
    public java.io.ByteArrayOutputStream Out;

    public ZipComponentWriter
      (
        java.util.zip.ZipOutputStream Parent,
        String Name,
        boolean Compressed
      )
      {
        this.Parent = Parent;
        Entry = new ZipEntry(Name);
        Entry.setMethod
          (
            Compressed ?
                ZipEntry.DEFLATED
            :
                ZipEntry.STORED
          );
        Out = new java.io.ByteArrayOutputStream();
      } /*ZipComponentWriter*/

    public void write
      (
        byte[] buffer,
        int offset,
        int len
      )
      /* writes more data making up the component. */
      {
        Out.write(buffer, offset, len);
      } /*write*/

    public void write
      (
        byte[] buffer
      )
      {
        write(buffer, 0, buffer.length);
      } /*write*/

    public void write
      (
        String data
      )
      {
        write(data.getBytes());
      } /*write*/

    public void close()
      /* finalizes output of this archive component. */
        throws java.io.IOException
      {
        Out.close();
        final byte[] TheData = Out.toByteArray();
        Entry.setSize(TheData.length);
        final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(TheData);
        Entry.setCrc(crc.getValue());
        Parent.putNextEntry(Entry);
        Parent.write(TheData, 0, TheData.length);
        Parent.closeEntry();
      } /*close*/

  } /*ZipComponentWriter*/

public class Persistent
  /* saving/loading of libraries and calculator state. */
  {
    public static final String CalcMimeType = "application/vnd.nz.gen.geek_central.ti5x";
    public static final String StateExt = ".ti5s"; /* for saved calculator state */
    public static final String ProgExt = ".ti5p"; /* for saved user program */
    public static final String LibExt = ".ti5l"; /* for library */
    public static final String ProgramsDir = "TI5X_Programs"; /* where to save user programs */
    public static final String ProgramsDir_Old = "Programs"; /* old Save Location (User Programs) */
    public static final String DataDir = "Download"; /* where to save exported data */
    public static final String[] ExternalCalcDirectories =
      /* where to load programs/libraries from */
        {
            ProgramsDir,
            DataDir,
        };
    public static final String[] ExternalDataDirectories =
      /* where to load data files from */
        {
            DataDir,
        };
    public static final String[] LikelyDataExts =
      /* most likely extensions for import data files */
        {
            ".dat",
            ".txt",
        };
    public static final String SavedStateName = "state" + StateExt;
      /* for saved state other than loaded library */
    public static final String SavedLibName = "module" + LibExt;
      /* loaded library saved separately so it doesn't have to be re-saved
        every time state changes */


    public static class DataFormatException extends RuntimeException
      /* indicates a problem parsing a saved state file. */
      {

        public DataFormatException
          (
            String Message
          )
          {
            super(Message);
          } /*DataFormatException*/

      } /*DataFormatException*/

    static void SaveBool
      (
        java.io.PrintStream POut,
        String Name,
        boolean Value,
        int Indent
      )
      {
        POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
        POut.printf(Global.StdLocale, "<param name=\"%s\" value=\"%s\"/>\n", Name, Value ? "1" : "0");
      } /*SaveBool*/

    static void SaveInt
      (
        java.io.PrintStream POut,
        String Name,
        int Value,
        int Indent
      )
      {
        POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
        POut.printf(Global.StdLocale, "<param name=\"%s\" value=\"%d\"/>\n", Name, Value);
      } /*SaveInt*/

    static void SaveNumber
      (
        java.io.PrintStream POut,
        String Name,
        Number Value,
        int Indent
      )
      {
        POut.printf(Global.StdLocale, String.format(Global.StdLocale, "%%%ds", Indent), " ");
        POut.printf
          ("<param name=\"%s\" value=\""
           + Value.formatString(Global.StdLocale, Global.NrSigFigures)
           + "\"/>\n", Name);
      } /*SaveNumber*/

    static boolean GetBool
      (
        String Value
      )
      {
        boolean Result;
        int IntValue;
        try
          {
            IntValue = Integer.parseInt(Value);
          }
        catch (NumberFormatException Bad)
          {
            throw new DataFormatException(String.format(Global.StdLocale, "bad boolean value \"%s\"", Value));
          } /*try*/
        if (IntValue == 0)
          {
            Result = false;
          }
        else if (IntValue == 1)
          {
            Result = true;
          }
        else
          {
            throw new DataFormatException(String.format(Global.StdLocale, "bad boolean value %d", IntValue));
          } /*if*/
        return
            Result;
      } /*GetBool*/

    static int GetInt
      (
        String Value
      )
      {
        int Result;
        try
          {
            Result = Integer.parseInt(Value);
          }
        catch (NumberFormatException Bad)
          {
            throw new DataFormatException(String.format(Global.StdLocale, "bad integer value \"%s\"", Value));
          } /*try*/
        return
            Result;
      } /*GetInt*/

    static double GetDouble
      (
        String Value
      )
      {
        double Result;
        try
          {
            Result = Double.parseDouble(Value);
          }
        catch (NumberFormatException Bad)
          {
            throw new DataFormatException(String.format(Global.StdLocale, "bad double value \"%s\"", Value));
          } /*try*/
        return
            Result;
      } /*GetDouble*/

    static void SaveProg
      (
        byte[] Program,
        java.io.PrintStream POut,
        int Indent
      )
      /* outputs a <prog> section to POut containing the contents of Program. Trailing
        zeroes are omitted. */
      {
        POut.print(String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds<prog>\n", Indent), ""));
        int Cols = 0;
        int i = 0;
        int LastNonzero = -1;
        for (;;)
          {
            if (i == Program.length)
              {
                if (Cols != 0)
                  {
                    POut.println();
                  } /*if*/
                break;
              } /*if*/
            if (Program[i] != 0)
              {
                for (int j = LastNonzero + 1; j <= i; ++j)
                  {
                    if (Cols == 24)
                      {
                        POut.print("\n");
                        Cols = 0;
                      } /*if*/
                    if (Cols != 0)
                      {
                        POut.print(" ");
                      }
                    else
                      {
                        POut.print
                          (
                            String.format
                              (
                                Global.StdLocale,
                                String.format(Global.StdLocale, "%%%ds", Indent + 4),
                                ""
                              )
                          );
                      } /*if*/
                    POut.printf(Global.StdLocale, "%02d", Program[j]);
                    ++Cols;
                  } /*for*/
                LastNonzero = i;
              } /*if*/
            ++i;
          } /*for*/
        POut.print
          (
            String.format(Global.StdLocale, String.format(Global.StdLocale, "%%%ds</prog>\n", Indent), "")
          );
      } /*SaveProg*/

/* Note Importer and Exporter states are NOT saved/restored */

    public static void Save
      (
        ButtonGrid Buttons, /* ignored unless CalcState */
        State Calc,
        boolean Libs, /* save currently-loaded library */
        boolean CalcState,
          /* true to save entire calculator state (apart from library),
            false to only save user-entered program */
        java.io.OutputStream RawOut
      )
      {
        try
          {
            java.util.zip.ZipOutputStream Out = new java.util.zip.ZipOutputStream(RawOut);
              {
              /* Follow ODF convention of an uncompressed "mimetype" entry at known offset
                to allow magic-number sniffing. Must be first. */
                final ZipComponentWriter MimeType = new ZipComponentWriter(Out, "mimetype", false);
                MimeType.write(CalcMimeType);
                MimeType.close();
              }
            if (Libs && Calc.ModuleHelp != null)
              {
                final ZipComponentWriter LibHelp = new ZipComponentWriter(Out, "help", true);
                LibHelp.write(Calc.ModuleHelp);
                LibHelp.close();
              } /*if*/
            for (int BankNr = 0;;)
              {
                if (BankNr == Calc.MaxBanks)
                    break;
                if (BankNr != 0 ? Libs && Calc.Bank[BankNr] != null : !Libs || CalcState)
                  {
                    final State.ProgBank Bank = Calc.Bank[BankNr];
                    if (Bank.Card != null)
                      {
                        final ZipComponentWriter CardOut =
                            new ZipComponentWriter
                              (
                                Out,
                                String.format(Global.StdLocale, "card%02d", BankNr),
                                true
                              );
                        Bank.Card.compress
                          (
                            /*format =*/ android.graphics.Bitmap.CompressFormat.PNG,
                              /* good enough, won't be large */
                            /*quality =*/ 100, /* ignored */
                            /*stream =*/ CardOut.Out
                          );
                        CardOut.close();
                      } /*if*/
                    if (BankNr != 0 && Bank.Program != null)
                      /* bank 0 program written out later */
                      {
                        final ZipComponentWriter ProgOut =
                            new ZipComponentWriter
                              (
                                Out,
                                String.format(Global.StdLocale, "prog%02d", BankNr),
                                true
                              );
                        java.io.PrintStream POut = new java.io.PrintStream(ProgOut.Out);
                        POut.println("<state>");
                        POut.println("    <calc>");
                        SaveProg(Bank.Program, POut, 8);
                        POut.println("    </calc>");
                        POut.println("</state>");
                        POut.flush();
                        ProgOut.close();
                      } /*if*/
                    if (Bank.Help != null)
                      {
                        final ZipComponentWriter BankHelp = new ZipComponentWriter
                          (
                            Out,
                            String.format(Global.StdLocale, "help%02d", BankNr),
                            true
                          );
                        BankHelp.write(Bank.Help);
                        BankHelp.close();
                      } /*if*/
                  } /*if*/
                if (!Libs) /* only bank 0 for programs */
                    break;
                ++BankNr;
              } /*for*/
            if (CalcState || !Libs)
              {
                final ZipComponentWriter StateOut = new ZipComponentWriter(Out, "prog00", true);
                java.io.PrintStream POut = new java.io.PrintStream(StateOut.Out);
                POut.println("<state>");
                if (CalcState)
                  {
                    POut.println("    <buttons>");
                    SaveBool(POut, "alt", Buttons.AltState, 8);
                    SaveBool(POut, "overlay", Buttons.OverlayVisible, 8);
                    SaveInt(POut, "selected_button", Buttons.SelectedButton, 8);
                    SaveInt(POut, "digits_needed", Buttons.DigitsNeeded, 8);
                    SaveBool(POut, "accept_symbolic", Buttons.AcceptSymbolic, 8);
                    SaveBool(POut, "accept_ind", Buttons.AcceptInd, 8);
                    SaveBool(POut, "next_literal", Buttons.NextLiteral, 8);
                    SaveInt(POut, "accum_digits", Buttons.AccumDigits, 8);
                    SaveInt(POut, "first_operand", Buttons.FirstOperand, 8);
                    SaveBool(POut, "got_first_operand", Buttons.GotFirstOperand, 8);
                    SaveBool(POut, "got_first_ind", Buttons.GotFirstInd, 8);
                    SaveBool(POut, "is_symbolic", Buttons.IsSymbolic, 8);
                    SaveBool(POut, "got_ind", Buttons.GotInd, 8);
                    SaveInt(POut, "collecting_for_function", Buttons.CollectingForFunction, 8);
                    POut.println("    </buttons>");
                  } /*if*/
                POut.println("    <calc>");
                if (CalcState)
                  {
                    {
                      String StateName;
                      switch (Calc.CurState)
                        {
                          case State.EntryState:
                            StateName = "entry";
                          break;
                          case State.DecimalEntryState:
                            StateName = "decimal_entry";
                          break;
                          case State.ExponentEntryState:
                            StateName = "exponent_entry";
                          break;
                          case State.ResultState:
                            StateName = "result";
                          break;
                          default:
                            throw new RuntimeException
                              (
                                String.format
                                  (
                                    Global.StdLocale,
                                    "unrecognized Calc.CurState = %d",
                                    Calc.CurState
                                  )
                              );
                          /* break; */
                        } /*switch*/
                      POut.printf
                        (
                          Global.StdLocale,
                          "        <param name=\"state\" value=\"%s\"/>\n",
                          StateName
                        );
                    }
                    SaveBool(POut, "exponent_entered", Calc.ExponentEntered, 8);
                    SaveBool(POut, "error_state", Calc.inError, 8);
                    if (Calc.CurState != State.ResultState)
                      {
                        POut.printf
                          (
                            Global.StdLocale,
                            "        <param name=\"display\" value=\"%s\"/>\n",
                            Calc.CurDisplay
                          );
                      } /*if*/
                    SaveBool(POut, "inv", Calc.InvState, 8);
                      {
                        String FmtName;
                        switch (Calc.CurFormat)
                          {
                            case State.FORMAT_FIXED:
                                FmtName = "fixed";
                            break;
                            case State.FORMAT_FLOAT:
                                FmtName = "float";
                            break;
                            case State.FORMAT_ENG:
                                FmtName = "eng";
                            break;
                            default:
                              throw new RuntimeException
                                (
                                  String.format(Global.StdLocale, "unrecognized Calc.CurFormat = %d", Calc.CurFormat)
                                );
                            /* break; */
                          } /*switch*/
                        POut.printf(Global.StdLocale, "        <param name=\"format\" value=\"%s\"/>\n", FmtName);
                      }
                    SaveInt(POut, "nr_decimals", Calc.CurNrDecimals, 8);
                      {
                        String Name;
                        switch (Calc.CurAng)
                          {
                            case State.ANG_RAD:
                              Name = "radians";
                            break;
                            case State.ANG_DEG:
                              Name = "degrees";
                            break;
                            case State.ANG_GRAD:
                              Name = "gradians";
                            break;
                            default:
                              throw new RuntimeException
                                (
                                  String.format(Global.StdLocale, "unrecognized Calc.Ang = %d", Calc.CurAng)
                                );
                            /* break; */
                          } /*switch*/
                        POut.printf
                          (
                            Global.StdLocale,
                            "        <param name=\"angle_units\" value=\"%s\"/>\n",
                            Name
                          );
                      }
                    POut.println("        <opstack>");
                    for (int i = 0; i < Calc.OpStackNext; ++i)
                      {
                        final State.OpStackEntry Op = Calc.OpStack[i];
                        String OpName;
                        switch (Op.Operator)
                          {
                            case State.STACKOP_ADD:
                              OpName = "add";
                            break;
                            case State.STACKOP_SUB:
                              OpName = "sub";
                            break;
                            case State.STACKOP_MUL:
                              OpName = "mul";
                            break;
                            case State.STACKOP_DIV:
                              OpName = "div";
                            break;
                            case State.STACKOP_MOD:
                              OpName = "mod";
                            break;
                            case State.STACKOP_EXP:
                              OpName = "exp";
                            break;
                            case State.STACKOP_ROOT:
                              OpName = "root";
                            break;
                            default:
                              throw new RuntimeException
                                (
                                  String.format(Global.StdLocale, "unrecognized stacked op %d at pos %d", Op.Operator, i)
                                );
                            /* break; */
                          } /*switch*/
                        POut.printf
                          (
                            Global.StdLocale,
                            "            <op name=\"%s\" opnd=\"%.16e\" parens=\"%d\"/>\n",
                            OpName,
                            Op.Operand.get(),
                            Op.ParenFollows
                          );
                      } /*for*/
                    POut.println("        </opstack>");
                    SaveNumber(POut, "X", Calc.X, 8);
                    SaveNumber(POut, "T", Calc.T, 8);
                    SaveBool(POut, "learn_mode", Calc.ProgMode, 8);
                    POut.println("        <mem>");
                    for (int i = 0; i < Calc.Memory.length; ++i)
                      {
                        POut.printf("            %s\n", Calc.Memory[i].formatString(Global.StdLocale, 16));
                      } /*for*/
                    POut.println("        </mem>");
                    POut.print("        <feedback kind=\"");
                    switch (Buttons.FeedbackType)
                      {
                        default:
                        case ButtonGrid.FEEDBACK_CLICK:
                          POut.print("click");
                        break;
                        case ButtonGrid.FEEDBACK_VIBRATE:
                          POut.print("vibrate");
                        break;
                        case ButtonGrid.FEEDBACK_BOTH:
                          POut.print("both");
                          break;
                        case ButtonGrid.FEEDBACK_NONE:
                          POut.print("none");
                        break;
                      } /*switch*/
                    POut.println("\"/>\n");
                  } /*if CalcState*/
                SaveProg(Calc.Program, POut, 8);
                if (CalcState)
                  {
                    POut.print("        <flags>\n            ");
                    for (int i = 0; i < Calc.Flag.length; ++i)
                      {
                        if (i != 0)
                          {
                            POut.print(" ");
                          } /*if*/
                        POut.print(Calc.Flag[i] ? "1" : "0");
                      } /*for*/
                    POut.print("\n        </flags>\n");
                    SaveInt(POut, "PC", Calc.PC, 8);
                    SaveInt(POut, "bank", Calc.CurBank, 8);
                    SaveInt(POut, "regoffset", Calc.RegOffset, 8);
                    POut.println("        <retstack>");
                    for (int i = 0; i <= Calc.ReturnLast; ++i)
                      {
                        final State.ReturnStackEntry Ret = Calc.ReturnStack[i];
                        POut.printf
                          (
                            "            <ret bank=\"%d\" addr=\"%d\" from_interactive=\"%s\"/>\n",
                            Ret.BankNr,
                            Ret.Addr,
                            Ret.FromInteractive ? "1" : "0"
                          );
                      } /*for*/
                    POut.println("        </retstack>");
                    POut.print("        <printreg>\n            ");
                    for (int i = 0; i < Calc.PrintRegister.length; ++i)
                      {
                        if (i != 0)
                          {
                            POut.print(" ");
                          } /*if*/
                        POut.printf("%d", Calc.PrintRegister[i]);
                      } /*for*/
                    POut.println("\n        </printreg>");
                  } /*if*/
                POut.println("    </calc>");
                POut.println("</state>");
                POut.flush();
                StateOut.close();
              } /*if*/
            Out.finish();
          }
        catch (java.io.IOException Failed)
          {
            throw new RuntimeException("ti5x.Persistent.Save error " + Failed.toString());
          } /*try*/
      } /*Save*/

    public static void Save
      (
        ButtonGrid Buttons, /* ignored unless CalcState */
        State Calc,
        boolean Libs, /* save currently-loaded library */
        boolean CalcState,
          /* true to save entire calculator state (apart from library),
            false to only save program */
        String ToFile
      )
      {
        java.io.FileOutputStream Out;
        try
          {
            Out = new java.io.FileOutputStream(ToFile);
          }
        catch (java.io.FileNotFoundException Failed)
          {
            throw new RuntimeException
              (
                "ti5x.Persistent.Save create error " + Failed.toString()
              );
          } /*try*/
        Save(Buttons, Calc, Libs, CalcState, Out);
        try
          {
            Out.flush();
            Out.close();
          }
        catch (java.io.IOException Failed)
          {
            throw new RuntimeException("ti5x.Persistent.Save error " + Failed.toString());
          } /*try*/
      } /*Save*/

    public static byte[] ReadAll
      (
        java.io.InputStream From
      )
      /* reads all available data from From. */
    throws java.io.IOException
      {
        java.io.ByteArrayOutputStream Result = new java.io.ByteArrayOutputStream();
        final byte[] Buf = new byte[256]; /* just to reduce number of I/O operations */
        for (;;)
          {
            final int BytesRead = From.read(Buf);
            if (BytesRead < 0)
                break;
            Result.write(Buf, 0, BytesRead);
          } /*for*/
        return
            Result.toByteArray();
      } /*ReadAll*/

    static class CalcStateLoader extends org.xml.sax.helpers.DefaultHandler
      {
        protected Display Disp;
        protected ButtonGrid Buttons;
        protected State Calc;
        protected int BankNr;
        protected boolean CalcState;

        private final int AtTopLevel = 0;
        private final int DoingState = 1;
        private final int DoingButtons = 2;
        private final int DoingCalc = 3;
        private final int DoingOpStack = 10;
        private final int DoingMem = 11;
        private final int DoingProg = 12;
        private final int DoingFlags = 13;
        private final int DoingRetStack = 14;
        private final int DoingPrintReg = 15;
        private final int DoingEmptyTag = 19;
        private int ParseState = AtTopLevel;
        private boolean DoneState = false;
        private boolean AllowContent;
        java.io.ByteArrayOutputStream Content = null;

        public CalcStateLoader
          (
            Display Disp,
            ButtonGrid Buttons,
            State Calc,
            int BankNr,
            boolean CalcState
          )
          {
            super();
            this.Disp = Disp;
            this.Buttons = Buttons;
            this.Calc = Calc;
            this.BankNr = BankNr;
            this.CalcState = CalcState;
          } /*CalcStateLoader*/

        private void StartContent()
          {
            Content = new java.io.ByteArrayOutputStream();
            AllowContent = true;
          } /*StartContent*/

        @Override
        public void startElement
          (
            String uri,
            String localName,
            String qName,
            org.xml.sax.Attributes attributes
          )
          {
            localName = localName.intern();
            boolean Handled = false;
            AllowContent = false; /* to begin with */
            if (ParseState == AtTopLevel)
              {
                if (localName == "state" && !DoneState)
                  {
                    ParseState = DoingState;
                    Handled = true;
                  } /*if*/
              }
            else if (ParseState == DoingState)
              {
                if (CalcState && localName == "buttons")
                  {
                    ParseState = DoingButtons;
                    Handled = true;
                  }
                else if (localName == "calc")
                  {
                    ParseState = DoingCalc;
                    Handled = true;
                  } /*if*/
              }
            else if (ParseState == DoingButtons)
              {
                if (localName == "param")
                  {
                    final String Name = attributes.getValue("name").intern();
                    final String Value = attributes.getValue("value");
                    if (Name == "alt")
                      {
                        Buttons.AltState = GetBool(Value);
                      }
                    else if (Name == "overlay")
                      {
                        Buttons.OverlayVisible = GetBool(Value);
                      }
                    else if (Name == "selected_button")
                      {
                        Buttons.SelectedButton = GetInt(Value);
                      }
                    else if (Name == "digits_needed")
                      {
                        Buttons.DigitsNeeded = GetInt(Value);
                      }
                    else if (Name == "accept_symbolic")
                      {
                        Buttons.AcceptSymbolic = GetBool(Value);
                      }
                    else if (Name == "accept_ind")
                      {
                        Buttons.AcceptInd = GetBool(Value);
                      }
                    else if (Name == "next_literal")
                      {
                        Buttons.NextLiteral = GetBool(Value);
                      }
                    else if (Name == "accum_digits")
                      {
                        Buttons.AccumDigits = GetInt(Value);
                      }
                    else if (Name == "first_operand")
                      {
                        Buttons.FirstOperand = GetInt(Value);
                      }
                    else if (Name == "got_first_operand")
                      {
                        Buttons.GotFirstOperand = GetBool(Value);
                      }
                    else if (Name == "got_first_ind")
                      {
                        Buttons.GotFirstInd = GetBool(Value);
                      }
                    else if (Name == "is_symbolic")
                      {
                        Buttons.IsSymbolic = GetBool(Value);
                      }
                    else if (Name == "got_ind")
                      {
                        Buttons.GotInd = GetBool(Value);
                      }
                    else if (Name == "collecting_for_function")
                      {
                        Buttons.CollectingForFunction = GetInt(Value);
                      }
                    else
                      {
                        throw new DataFormatException
                          (
                            String.format(Global.StdLocale, "unrecognized <buttons> param \"%s\"", Name)
                          );
                      } /*if*/
                    Handled = true;
                  } /*if*/
              }
            else if (ParseState == DoingCalc)
              {
                if (CalcState && localName == "param")
                  {
                    final String Name = attributes.getValue("name").intern();
                    final String Value = attributes.getValue("value").intern();
                    if (Name == "state")
                      {
                        if (Value == "entry")
                          {
                            Calc.CurState = State.EntryState;
                          }
                        else if (Value == "decimal_entry")
                          {
                            Calc.CurState = State.DecimalEntryState;
                          }
                        else if (Value == "exponent_entry")
                          {
                            Calc.CurState = State.ExponentEntryState;
                          }
                        else if (Value == "result")
                          {
                            Calc.CurState = State.ResultState;
                          }
                        else if (Value == "error")
                          {
                            // for compatibility with previous version
                            Calc.inError = true;
                          }
                        else
                          {
                            throw new RuntimeException
                              (
                                String.format(Global.StdLocale, "unrecognized calc state \"%s\"", Value)
                              );
                          } /*if*/
                      }
                    else if (Name == "exponent_entered")
                      {
                        Calc.ExponentEntered = GetBool(Value);
                      }
                    else if (Name == "error_state")
                      {
                        Calc.inError = GetBool(Value);
                      }
                    else if (Name == "display")
                      {
                        Calc.CurDisplay = Value;
                      }
                    else if (Name == "inv")
                      {
                        Calc.InvState = GetBool(Value);
                      }
                    else if (Name == "format")
                      {
                        if (Value == "fixed")
                          {
                            Calc.CurFormat = State.FORMAT_FIXED;
                          }
                        else if (Value == "float")
                          {
                            Calc.CurFormat = State.FORMAT_FLOAT;
                          }
                        else if (Value == "eng")
                          {
                            Calc.CurFormat = State.FORMAT_ENG;
                          }
                        else
                          {
                            throw new RuntimeException
                              (
                                String.format(Global.StdLocale, "unrecognized calc format \"%s\"", Value)
                              );
                          } /*if*/
                      }
                    else if (Name == "nr_decimals")
                      {
                        Calc.CurNrDecimals = GetInt(Value);
                      }
                    else if (Name == "angle_units")
                      {
                        if (Value == "radians")
                          {
                            Calc.CurAng = State.ANG_RAD;
                          }
                        else if (Value == "degrees")
                          {
                            Calc.CurAng = State.ANG_DEG;
                          }
                        else if (Value == "gradians")
                          {
                            Calc.CurAng = State.ANG_GRAD;
                          }
                        else
                          {
                            throw new RuntimeException
                              (
                                String.format(Global.StdLocale, "unrecognized calc angle_units \"%s\"", Value)
                              );
                          } /*if*/
                      }
                    else if (Name == "X")
                      {
                        Calc.X = new Number(Value);
                      }
                    else if (Name == "T")
                      {
                        Calc.T = new Number(Value);
                      }
                    else if (Name == "learn_mode")
                      {
                        Calc.ProgMode = GetBool(Value);
                      }
                    else if (Name == "PC")
                      {
                        Calc.PC = GetInt(Value);
                      }
                    else if (Name == "bank")
                      {
                        Calc.CurBank = GetInt(Value);
                      }
                    else if (Name == "regoffset")
                      {
                        Calc.RegOffset = GetInt(Value);
                      }
                    else
                      {
                        throw new DataFormatException
                          (
                            String.format(Global.StdLocale, "unrecognized <calc> param \"%s\"", Name)
                          );
                      } /*if*/
                    Handled = true;
                  }
                else if (CalcState && localName == "opstack")
                  {
                    Calc.OpStackNext = 0;
                    ParseState = DoingOpStack;
                    Handled = true;
                  }
                else if (CalcState && localName == "mem")
                  {
                    ParseState = DoingMem;
                    StartContent();
                    Handled = true;
                  }
                else if (CalcState && localName == "feedback")
                  {
                    final String Kind = attributes.getValue("kind").intern();
                    if (Kind == "none")
                      {
                        Buttons.FeedbackType = ButtonGrid.FEEDBACK_NONE;
                      }
                    else if (Kind == "vibrate")
                      {
                        Buttons.FeedbackType = ButtonGrid.FEEDBACK_VIBRATE;
                      }
                    else if (Kind == "both")
                      {
                        Buttons.FeedbackType = ButtonGrid.FEEDBACK_BOTH;
                      }
                    else
                      {
                        Buttons.FeedbackType = ButtonGrid.FEEDBACK_CLICK;
                      } /*if*/
                    ParseState = DoingEmptyTag;
                    Handled = true;
                  }
                else if (localName == "prog") /* only one allowed if not CalcState */
                  {
                    ParseState = DoingProg;
                    StartContent();
                    Handled = true;
                  }
                else if (CalcState && localName == "flags")
                  {
                    ParseState = DoingFlags;
                    StartContent();
                    Handled = true;
                  }
                else if (CalcState && localName == "retstack")
                  {
                    Calc.ReturnLast = -1;
                    ParseState = DoingRetStack;
                    Handled = true;
                  }
                else if (CalcState && localName == "printreg")
                  {
                    ParseState = DoingPrintReg;
                    StartContent();
                    Handled = true;
                  } /*if*/
              }
            else if (ParseState == DoingOpStack)
              {
                if (localName == "op")
                  {
                    final String OpName = attributes.getValue("name").intern();
                    int Op;
                    if (OpName == "add")
                      {
                        Op = State.STACKOP_ADD;
                      }
                    else if (OpName == "sub")
                      {
                        Op = State.STACKOP_SUB;
                      }
                    else if (OpName == "mul")
                      {
                        Op = State.STACKOP_MUL;
                      }
                    else if (OpName == "div")
                      {
                        Op = State.STACKOP_DIV;
                      }
                    else if (OpName == "mod")
                      {
                        Op = State.STACKOP_MOD;
                      }
                    else if (OpName == "exp")
                      {
                        Op = State.STACKOP_EXP;
                      }
                    else if (OpName == "root")
                      {
                        Op = State.STACKOP_ROOT;
                      }
                    else
                      {
                        throw new DataFormatException
                          (
                            String.format(Global.StdLocale, "unrecognized <op> operator \"%s\"", OpName)
                          );
                      } /*if*/
                    if (Calc.OpStackNext == Calc.MaxOpStack)
                      {
                        throw new DataFormatException
                          (
                            String.format(Global.StdLocale, "too many <op> entries -- only %d allowed", Calc.MaxOpStack)
                          );
                      } /*if*/
                    Calc.OpStack[Calc.OpStackNext++] = new State.OpStackEntry
                      (
                        new Number(attributes.getValue("opnd")),
                        Op,
                        GetInt(attributes.getValue("parens"))
                      );
                    Handled = true;
                  } /*if*/
              }
            else if (ParseState == DoingRetStack)
              {
                if (localName == "ret")
                  {
                    if (Calc.ReturnLast == Calc.MaxReturnStack - 1)
                      {
                        throw new DataFormatException
                          (
                            String.format(Global.StdLocale, "too many <ret> entries -- only %d allowed", Calc.MaxReturnStack)
                          );
                      } /*if*/
                    Calc.ReturnStack[++Calc.ReturnLast] = new State.ReturnStackEntry
                      (
                        GetInt(attributes.getValue("bank")),
                        GetInt(attributes.getValue("addr")),
                        GetBool(attributes.getValue("from_interactive"))
                      );
                    Handled = true;
                  } /*if*/
              } /*if*/
            if (!Handled)
              {
                throw new DataFormatException("unexpected XML tag " + localName + " in state " + ParseState);
              } /*if*/
          } /*startElement*/

        @Override
        public void characters
          (
            char[] ch,
            int start,
            int length
          )
          {
            if (AllowContent)
              {
                try
                  {
                    Content.write(new String(ch, start, length).getBytes());
                  }
                catch (java.io.IOException Failed)
                  {
                    throw new RuntimeException("ti5x XML content parse error " + Failed.toString());
                  } /*try*/
              } /*if*/
            /* else ignore */
          } /*characters*/

        @Override
        public void endElement
          (
            String uri,
            String localName,
            String qName
          )
          {
            localName = localName.intern();
            final String ContentStr = Content != null ? Content.toString() : null;
            Content = null;
            AllowContent = false;
            switch (ParseState)
              {
                case DoingState:
                  if (localName == "state")
                    {
                      ParseState = AtTopLevel;
                      DoneState = true;
                    } /*if*/
                break;
                case DoingButtons:
                  if (localName == "buttons")
                    {
                      ParseState = DoingState;
                    } /*if*/
                break;
                case DoingCalc:
                  if (localName == "calc")
                    {
                      ParseState = DoingState;
                    } /*if*/
                break;
                case DoingOpStack:
                  if (localName == "opstack")
                    {
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingRetStack:
                  if (localName == "retstack")
                    {
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingPrintReg:
                  if (localName == "printreg")
                    {
                      int Place = 0;
                      int i = 0;
                      for (;;)
                        {
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) > ' ')
                                  break;
                              ++i;
                            } /*for*/
                          final int Start = i;
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) <= ' ')
                                  break;
                              ++i;
                            } /*for*/
                          if (i > Start)
                            {
                              if (Place == Calc.PrintRegister.length)
                                {
                                  throw new DataFormatException
                                    (
                                      String.format
                                        (
                                          Global.StdLocale,
                                          "too many columns in print register, only %d allowed",
                                          Calc.PrintRegister.length
                                        )
                                    );
                                } /*if*/
                              Calc.PrintRegister[Place++] =
                                  (byte)GetInt(ContentStr.substring(Start, i));
                            } /*if*/
                          if (i == ContentStr.length())
                              break;
                        } /*for*/
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingMem:
                  if (localName == "mem")
                    {
                      int Reg = 0;
                      int i = 0;
                      for (;;)
                        {
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) > ' ')
                                  break;
                              ++i;
                            } /*for*/
                          final int Start = i;
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) <= ' ')
                                  break;
                              ++i;
                            } /*for*/
                          if (i > Start)
                            {
                              if (Reg == Calc.MaxMemories)
                                {
                                  throw new DataFormatException
                                    (
                                      String.format(Global.StdLocale, "too many memories, only %d allowed", Calc.MaxMemories)
                                    );
                                } /*if*/
                              Calc.Memory[Reg++] = new Number(ContentStr.substring(Start, i));
                            } /*if*/
                          if (i == ContentStr.length())
                              break;
                        } /*for*/
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingProg:
                  if (localName == "prog")
                    {
                      java.util.ArrayList<Byte> Prog = null;
                      if (BankNr == 0)
                        {
                          for (int i = 0; i < Calc.MaxProgram; ++i)
                            {
                              Calc.Program[i] = (byte)0;
                            } /*for*/
                        }
                      else
                        {
                          Prog = new java.util.ArrayList<Byte>();
                            /* will be restricted to 1000 steps below */
                        } /*if*/
                      int Addr = 0;
                      int i = 0;
                      for (;;)
                        {
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) > ' ')
                                  break;
                              ++i;
                            } /*for*/
                          final int Start = i;
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) <= ' ')
                                  break;
                              ++i;
                            } /*for*/
                          if (i > Start)
                            {
                              if (BankNr == 0 ? Addr == Calc.MaxProgram : Addr == 1000)
                                {
                                  throw new DataFormatException
                                    (
                                      String.format
                                        (
                                          Global.StdLocale,
                                          "too many program steps, only %d allowed",
                                          BankNr == 0 ? Calc.MaxProgram : 1000
                                        )
                                    );
                                } /*if*/
                              final byte val = (byte)GetInt(ContentStr.substring(Start, i));
                              if (BankNr != 0)
                                {
                                  Prog.add(val);
                                }
                              else
                                {
                                  Calc.Program[Addr++] = val;
                                } /*if*/
                            } /*if*/
                          if (i == ContentStr.length())
                              break;
                        } /*for*/
                      if (BankNr != 0)
                        {
                          Calc.Bank[BankNr].Program = new byte[Prog.size()];
                          for (i = 0; i < Prog.size(); ++i)
                            {
                              Calc.Bank[BankNr].Program[i] = Prog.get(i);
                            } /*for*/
                        } /*if*/
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingFlags:
                  if (localName == "flags")
                    {
                      int Flag = 0;
                      int i = 0;
                      for (;;)
                        {
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) > ' ')
                                  break;
                              ++i;
                            } /*for*/
                          final int Start = i;
                          for (;;)
                            {
                              if (i == ContentStr.length())
                                  break;
                              if (ContentStr.charAt(i) <= ' ')
                                  break;
                              ++i;
                            } /*for*/
                          if (i > Start)
                            {
                              if (Flag == Calc.MaxFlags)
                                {
                                  throw new DataFormatException
                                    (
                                      String.format(Global.StdLocale, "too many flags, only %d allowed", Calc.MaxFlags)
                                    );
                                } /*if*/
                              Calc.Flag[Flag++] = GetBool(ContentStr.substring(Start, i));
                            } /*if*/
                          if (i == ContentStr.length())
                              break;
                        } /*for*/
                      ParseState = DoingCalc;
                    } /*if*/
                break;
                case DoingEmptyTag:
                  ParseState = DoingCalc;
                break;
              } /*switch*/
          } /*endElement*/

      } /*CalcStateLoader*/

    public static class Load extends Global.Task
      {
        private final String FromFile;
        private final boolean Libs;
        private final boolean CalcState;
        private final Display Disp;
        private final ButtonGrid Buttons;
        private final State Calc;

        public Load
          (
            String FromFile,
            boolean Libs, /* true to load nonzero program banks, false to load bank 0 */
            boolean CalcState, /* true to load all state (including all available program banks) */
            Display Disp,
            ButtonGrid Buttons,
            State Calc
          )
          {
            this.FromFile = FromFile;
            this.Libs = Libs;
            this.CalcState = CalcState;
            this.Disp = Disp;
            this.Buttons = Buttons;
            this.Calc = Calc;
          } /*Load*/

        @Override
        public boolean PreRun()
          {
            if (CalcState)
              {
                Buttons.Reset();
                Calc.Reset(Libs);
              }
            else if (Libs)
              {
                Calc.ResetLibs();
              }
            else
              {
                Calc.ResetLabels(); /* at least */
              } /*if*/
            return
                true;
          } /*PreRun*/

        @Override
        public void BGRun()
          {
            try
              {
                try
                  {
                    final java.util.zip.ZipFile In = new java.util.zip.ZipFile
                      (
                        new java.io.File(FromFile),
                        java.util.zip.ZipFile.OPEN_READ
                      );
                    final ZipEntry MimeType = In.getEntry("mimetype");
                    if (MimeType == null)
                      {
                        throw new DataFormatException
                          (
                            "missing mandatory archive component: mimetype"
                          );
                      } /*if*/
                    if
                      (
                            In.entries().nextElement().getName().intern() != "mimetype"
                        ||
                            MimeType.getMethod() != ZipEntry.STORED
                      )
                      {
                        throw new DataFormatException("mimetype must be uncompressed and first in archive");
                      } /*if*/
                    if (new String(ReadAll(In.getInputStream(MimeType))).intern() != CalcMimeType)
                      {
                        throw new DataFormatException("wrong MIME type");
                      } /*if*/
                    if (Libs)
                      {
                        final ZipEntry LibHelpEntry = In.getEntry("help");
                        if (LibHelpEntry != null)
                          {
                            Calc.ModuleHelp = ReadAll(In.getInputStream(LibHelpEntry));
                          } /*if*/
                      } /*if*/
                    for (int BankNr = 0;;)
                      {
                        if (BankNr == Calc.MaxBanks)
                            break;
                        if (BankNr != 0 ? Libs : !Libs || CalcState)
                          {
                            final ZipEntry StateEntry =
                                In.getEntry(String.format(Global.StdLocale, "prog%02d", BankNr));
                            if (StateEntry != null)
                              {
                                final ZipEntry CardEntry =
                                    In.getEntry(String.format(Global.StdLocale, "card%02d", BankNr));
                                final ZipEntry HelpEntry =
                                    In.getEntry(String.format(Global.StdLocale, "help%02d", BankNr));
                                android.graphics.Bitmap CardImage = null;
                                byte[] BankHelp = null;
                                if (CardEntry != null)
                                  {
                                    CardImage = android.graphics.BitmapFactory.decodeStream
                                      (
                                        In.getInputStream(CardEntry)
                                      );
                                  } /*if*/
                                if (HelpEntry != null)
                                  {
                                    BankHelp = ReadAll(In.getInputStream(HelpEntry));
                                  } /*if*/
                                if (BankNr != 0)
                                  {
                                    Calc.Bank[BankNr] =
                                        new State.ProgBank(null /* filled in below */, CardImage, BankHelp);
                                  }
                                else
                                  {
                                  /* don't overwrite Calc.Bank[0].Program */
                                    Calc.Bank[0].Card = CardImage;
                                    Calc.Bank[0].Help = BankHelp;
                                  } /*if*/
                                try
                                  {
                                    javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse
                                      (
                                        In.getInputStream(StateEntry),
                                        new CalcStateLoader(Disp, Buttons, Calc, BankNr, CalcState)
                                      );
                                  }
                                catch (javax.xml.parsers.ParserConfigurationException Bug)
                                  {
                                    throw new RuntimeException("SAX parser error: " + Bug.toString());
                                  } /*try*/
                                catch (org.xml.sax.SAXException Bad)
                                  {
                                    throw new DataFormatException("SAX parser error: " + Bad.toString());
                                  } /*catch*/
                              }
                            else if (BankNr == 0)
                              {
                                throw new DataFormatException
                                  (
                                    "missing mandatory archive component: prog00"
                                  );
                              } /*if*/
                          } /*if*/
                        if (!Libs) /* only bank 0 for programs */
                            break;
                        ++BankNr;
                      } /*for*/
                  }
                catch (java.io.IOException IOError)
                  {
                    throw new DataFormatException("I/O error: " + IOError.toString());
                  } /*try*/
              }
            catch (DataFormatException Failure)
              {
                SetStatus(-1, Failure);
              } /*try*/
          } /*Run*/

        @Override
        public void PostRun()
          {
            if (TaskFailure == null)
              {
                if (Calc != null)
                  {
                    if (Calc.Bank[Calc.CurBank] == null)
                      Calc.CurBank = 1;
                    Calc.SelectProgram(Calc.CurBank, false);
                    switch (Calc.CurState)
                      {
                    case State.ResultState:
                        Calc.SetX(Calc.X, false);
                    break;
                      } /*switch*/
                    if (Calc.inError)
                      {
                        Calc.SetX(Calc.X, false);
                        Calc.SetErrorState(false);
                      }
                    Calc.SetProgMode(Calc.ProgMode);
                  } /*if*/
                if (Buttons != null)
                  {
                    if (CalcState)
                      {
                        Buttons.SetFeedbackType(Buttons.FeedbackType);
                      } /*if*/
                    Buttons.invalidate();
                  } /*if*/
              } /*if*/
          } /*PostRun*/
      } /*Load*/

    public static class LoadBuiltin extends Global.Task
      /* loads the included Master Library module into the calculator state. */
      {
        private final Load DoLoad;
        private final android.content.Context ctx;
        private final int SelId;
        private final boolean IsLib;
      /* Unfortunately java.util.zip.ZipFile can't read from an arbitrary InputStream,
        so I need to make a temporary copy of the master library out of my raw resources. */
        private final String TempLibName = "temp.ti5x"; /* name for temporary copy */

        public LoadBuiltin
          (
            android.content.Context ctx,
            boolean IsLib,
            int Id // Id number for the built-in program to load
          )
          {
            this.ctx = ctx;
            this.IsLib = IsLib;
            this.SelId = Id;
            final String TempLibFile = ctx.getFilesDir().getAbsolutePath() + "/" + TempLibName;
            DoLoad = new Load
              (
                /*FromFile =*/ TempLibFile,
                /*Libs =*/ IsLib,
                /*CalcState =*/ false,
                /*Disp =*/ Global.Disp,
                /*Buttons =*/ Global.Buttons,
                /*Calc =*/ Global.Calc
              );
          } /*LoadBuiltinLibrary*/

        @Override
        public boolean PreRun()
          {
            return
                DoLoad.PreRun();
          } /*PreRun*/

        @Override
        public void BGRun()
          {
            BuiltinLibrary Prog = IsLib ? Main.BuiltinLibraries[SelId] : Main.BuiltinPrograms[SelId];
            try
              {
                final java.io.InputStream LibFile = Prog.getInputStream(ctx);
                final java.io.OutputStream TempLib =
                    ctx.openFileOutput(TempLibName, ctx.MODE_PRIVATE);
                  {
                    byte[] Buffer = new byte[2048]; /* some convenient size */
                    for (;;)
                      {
                        final int NrBytes = LibFile.read(Buffer);
                        if (NrBytes <= 0)
                            break;
                        TempLib.write(Buffer, 0, NrBytes);
                      } /*for*/
                  }
                TempLib.flush();
                TempLib.close();
              }
            catch (java.io.FileNotFoundException Failed)
              {
                throw new RuntimeException("ti5x " + Prog.getName(ctx)
                                           + " load failed: " + Failed.toString());
              }
            catch (java.io.IOException Failed)
              {
                throw new RuntimeException("ti5x "+ Prog.getName(ctx)
                                           + " load failed: " + Failed.toString());
              } /*try*/
            DoLoad.BGRun();
            ctx.deleteFile(TempLibName);
          } /*BGRun*/

        @Override
        public void PostRun()
          {
            DoLoad.PostRun();
            // we have loaded a new built-in library, select its first program
            Global.Calc.SelectProgram(1, false);
          } /*PostRun*/

      } /*LoadBuiltinProgram*/

    public static void SaveState
      (
        android.content.Context ctx,
        boolean SaveLib /* true to save loaded library, false to save rest of state */
      )
      /* saves the current calculator state for later restoration. */
      {
        java.io.FileOutputStream CurSave;
        try
          {
            final String StateName =
                SaveLib ?
                    SavedLibName
                :
                    SavedStateName;
            ctx.deleteFile(StateName);
            CurSave = ctx.openFileOutput(StateName, ctx.MODE_PRIVATE);
          }
        catch (java.io.FileNotFoundException Eh)
          {
            throw new RuntimeException("ti5x save-state create error " + Eh.toString());
          } /*try*/
        Save
          (
            /*Buttons =*/ Global.Buttons,
            /*Calc =*/ Global.Calc,
            /*Libs =*/ SaveLib,
            /*CalcState =*/ !SaveLib,
            /*RawOut =*/ CurSave
          ); /* catch RuntimeException? */
        try
          {
            CurSave.flush();
            CurSave.close();
          }
        catch (java.io.IOException Failed)
          {
            throw new RuntimeException
              (
                "ti5x state save error " + Failed.toString()
              );
          } /*try*/
      } /*SaveState*/

    public static class RestoreState extends Global.Task
      /* restores the entire calculator state, using the previously-saved
        state if available, otherwise (re)initializes to default state. */
      {
        private final android.content.Context ctx;

        private static final int LOAD_LIB = 0;
        private static final int LOAD_STATE = 1;
        private static final int LOAD_MASTER = 2;
        private static final int SAVE_STATE = 3;
        private static final int RESTORE_DONE = 4;
        private int Restoring;

        private boolean RestoredLib;
        String StateFile = null;
        private Global.Task Subtask;

        private RestoreState
          (
            android.content.Context ctx,
            int Restoring,
            boolean RestoredLib
          )
          {
            this.ctx = ctx;
            this.Restoring = Restoring;
            this.RestoredLib = RestoredLib;
            Subtask = null;
          } /*RestoreState*/

        public RestoreState
          (
            android.content.Context ctx
          )
          {
            this(ctx, LOAD_LIB, false);
          } /*RestoreState*/

        @Override
        public boolean PreRun()
          {
            for (;;)
              {
                switch (Restoring)
                  {
                    case LOAD_LIB:
                    case LOAD_STATE:
                      /* load library before rest of state, otherwise Calc.SelectProgram(Calc.CurBank)
                        call (above) will trigger error on nonexistent bank */
                      final boolean LoadingLib = Restoring == LOAD_LIB;
                      StateFile =
                              ctx.getFilesDir().getAbsolutePath()
                          +
                              "/"
                          +
                              (LoadingLib ?
                                  SavedLibName
                              :
                                  SavedStateName
                              );
                      if (new java.io.File(StateFile).exists())
                        {
                          Subtask = new Load
                            (
                              /*FromFile =*/ StateFile,
                              /*Libs =*/ LoadingLib,
                              /*CalcState =*/ !LoadingLib,
                              /*Disp =*/ Global.Disp,
                              /*Buttons =*/ Global.Buttons,
                              /*Calc =*/ Global.Calc
                            );
                        } /*if*/
                    break;
                    case LOAD_MASTER:
                      if (!RestoredLib)
                        {
                        /* initialize state to include Master Library */
                       Subtask = new LoadBuiltin(ctx, true, Main.BUILTIN_MASTER_LIBRARY_INDEX);
                        } /*if*/
                    break;
                    case SAVE_STATE:
                      /* save newly-initialized state */
                      Subtask = new Global.Task()
                        {
                          @Override
                          public void BGRun()
                            {
                              SaveState(ctx, true);
                            } /*BGRun*/
                        } /*Global.Task*/;
                    break;
                  } /*switch*/
                if (Subtask != null || Restoring >= LOAD_MASTER)
                    break;
                ++Restoring;
              } /*for*/
            return
                Subtask != null && Subtask.PreRun();
          } /*PreRun*/

        @Override
        public void BGRun()
          {
            if (Subtask != null)
              {
                Subtask.BGRun();
                if (Subtask.TaskFailure == null && Restoring == LOAD_LIB)
                  {
                    RestoredLib = true;
                  } /*if*/
              } /*if*/
          } /*BGRun*/

        @Override
        public void PostRun()
          {
            if (Subtask != null)
              {
                if (Subtask.TaskFailure != null && StateFile != null)
                  {
                    System.err.printf
                      (
                        "ti5x failure to reload state from file \"%s\": %s\n",
                        StateFile,
                        Subtask.TaskFailure.toString()
                      ); /* debug  */
                  } /*if*/
                Subtask.PostRun();
                if (Restoring != RESTORE_DONE)
                  {
                  /* run the next stage */
                    Global.StartBGTask(new RestoreState(ctx, Restoring + 1, RestoredLib), null);
                  } /*if*/
              } /*if*/
          } /*PostRun*/
      } /*RestoreState*/

    public static void ResetState
      (
        android.content.Context ctx
      )
      /* wipes saved state. */
      {
        ctx.deleteFile(SavedStateName);
        ctx.deleteFile(SavedLibName);
      } /*ResetState*/



  } /*Persistent*/
