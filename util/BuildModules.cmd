@Echo Off
chcp 65001
Echo "Running UTF-8 Redirect"
Echo Command    %1%
Echo PyCommand  %2%
Echo PyArgs     %3%
Echo STDIN(0)   %4%
Echo STDOUT(1)  %5%
Echo STDERR(2)  %6%
SET PYTHONIOENCODING=UTF-8
%1 %2 %3 < %4 > %5 2> %6
