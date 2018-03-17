package net.obry.ti5x;
/*
    Saving/loading of programs, program libraries and calculator state

    Copyright 2015 Pascal Obry <pascal@obry.net>

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

class BuiltinLibrary
{
    int name;
    int lib;

    public BuiltinLibrary (int name, int lib)
    {
      this.name = name;
      this.lib = lib;
    }

    public String getName(android.content.Context ctx)
    {
      return ctx.getString(name);
    }

    public java.io.InputStream getInputStream(android.content.Context ctx)
    {
      return ctx.getResources().openRawResource(lib);
    }
}
