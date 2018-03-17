############################################################################
#                                   TI-59                                  #
#                                                                          #
#                      Copyright (C) 2014, Pascal Obry                     #
#                                                                          #
#  This is free software;  you can redistribute it  and/or modify it       #
#  under terms of the  GNU General Public License as published  by the     #
#  Free Software  Foundation;  either version 3,  or (at your option) any  #
#  later version.  This software is distributed in the hope  that it will  #
#  be useful, but WITHOUT ANY WARRANTY;  without even the implied warranty #
#  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU     #
#  General Public License for  more details.                               #
#                                                                          #
#  You should have  received  a copy of the GNU General  Public  License   #
#  distributed  with  this  software;   see  file COPYING3.  If not, go    #
#  to http://www.gnu.org/licenses for a complete copy of the license.      #
############################################################################

.SILENT:

MAKEFILES = $(wildcard */prog)

MAKEPROG=../util/makeprog

all: $(MAKEFILES)

force:

$(MAKEFILES): force
	echo Create $(patsubst %/,%,$(dir $@)).ti5p
	$(MAKEPROG) $(patsubst %/,%,$(dir $@)).ti5p \
		help00=$(patsubst %/,%,$(dir $@))/help \
		cardsrc00=$(patsubst %/,%,$(dir $@))/card \
		progsrc00=$(patsubst %/,%,$(dir $@))/prog

clean:
	rm -f *.ti5p
