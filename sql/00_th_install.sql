-- ============================================================================
--  Thunder · Oracle 21c · Service PDBORCL · Schema DEMO
--  00 – Komplettinstallation (als DEMO in SQL Developer mit F5 oder SQL*Plus)
-- ============================================================================

-- sqlplus DEMO@//localhost:1521/PDBORCL @00_th_install.sql
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
@@99_th_drop.sql
@@01_th_schema.sql
@@02_th_data.sql
@@04_th_3d.sql
WHENEVER SQLERROR CONTINUE
@@03_th_check.sql
