package net.obry.ti5x;
/*
    ti5x calculator emulator -- virtual printer display

    Copyright 2011       Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
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

import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;

public class PrinterView extends android.app.Activity
  {
    android.widget.ScrollView PaperScroll;
    PaperView ThePaper;
    Button ClearButton;
    Button TearButton;
    ToggleButton TraceButton;

    boolean FirstView = true;

    class PaperChangedListener implements Printer.Notifier
      {

        public void PaperChanged()
          {
            PaperScroll.scrollTo(0, ThePaper.GetViewHeight());
            ThePaper.invalidate();

          } /*PaperChanged*/

      } /*PaperChangedListener*/

    @Override
    public void onCreate
      (
        android.os.Bundle savedInstanceState
      )
      {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.printer);
        PaperScroll = (android.widget.ScrollView)findViewById(R.id.paper_scroll);
        ThePaper = (PaperView)findViewById(R.id.paper);
        addListenerOnButtons();

      } /*onCreate*/

    public void addListenerOnButtons()
      {
      /* 20161211 SJZ -
       * Saves Papertape Image and Clears Scroll
       */

      TearButton = (Button) findViewById(R.id.TearTapeButton);
      TearButton.setOnClickListener
        (
          new View.OnClickListener()
          {

            @Override
            public void onClick(View arg0)
              {
                Global.Print.Advance();
                Global.Print.Advance();
                Global.Print.SavePaper(getApplicationContext());
                PaperScroll.scrollTo(0, ThePaper.GetViewHeight());
              }

          }
        );

      /* 20161211 SJZ -
       * Clears and re-initializes the Paper Tape
       */

      ClearButton = (Button) findViewById(R.id.ClearTapeButton);
      ClearButton.setOnClickListener
        (
          new View.OnClickListener()
          {

            @Override
            public void onClick(View arg0)
              {
                Global.Print.ClearPaper(getApplicationContext());
                ThePaper.invalidate();
                PaperScroll.scrollTo(0, ThePaper.GetViewHeight());
              }
          }
        );

      /* 20161211 SJZ -
       * Enables Toggle of Tracing
       */

      TraceButton = (ToggleButton) findViewById(R.id.TracePrintButton);
      TraceButton.setChecked(Global.Calc.TracePrintActivated);
      TraceButton.setOnClickListener
        (
          new View.OnClickListener()
            {

              @Override
              public void onClick(View arg0)
                {
                  Global.Calc.TracePrintActivated = !Global.Calc.TracePrintActivated;
                  TraceButton.setChecked(Global.Calc.TracePrintActivated);
                }
            }
        );

    } /*AddListenerOnButtons*/


    @Override
    public void onPause()
      {
        super.onPause();
        if (Global.Print != null)
          {
            Global.Print.PrintListener = null;
          } /*if*/
      } /*onPause*/

    @Override
    public void onResume()
      {
        super.onResume();
        if (Global.Print != null)
          {
            Global.Print.PrintListener = new PaperChangedListener();
          } /*if*/
      } /*onResume*/

    @Override
    public void onWindowFocusChanged
      (
        boolean HasFocus
      )
      {
        super.onWindowFocusChanged(HasFocus);
        if (HasFocus && FirstView)
          {
            PaperScroll.fullScroll(android.view.View.FOCUS_DOWN);
            FirstView = false;
          } /*if*/
      } /*onWindowFocusChanged*/

  } /*PrinterView*/
