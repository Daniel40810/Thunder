-- ============================================================================
--  Thunder · Oracle 21c · Service PDBORCL · Schema DEMO
--  99 – Alles entfernen (Neuinstallation). Achtung: löscht auch das Blitzprotokoll TH_STRIKE.
-- ============================================================================

BEGIN
  FOR r IN (SELECT 'DROP VIEW ' || VIEW_NAME AS stmt FROM USER_VIEWS WHERE VIEW_NAME IN ('TH_STRIKE_DAY_V','TH_STRIKE_ORT_V')
            UNION ALL
            SELECT 'DROP TABLE ' || t || ' CASCADE CONSTRAINTS PURGE'
              FROM (SELECT 'TH_VIEW' t FROM DUAL UNION ALL SELECT 'TH_STRIKE' FROM DUAL UNION ALL SELECT 'TH_PALETTE' FROM DUAL
                    UNION ALL SELECT 'TH_SCENE_OBJECT' FROM DUAL UNION ALL SELECT 'TH_SCENE' FROM DUAL
                    UNION ALL SELECT 'TH_STORM_LEVEL' FROM DUAL)
             WHERE t IN (SELECT TABLE_NAME FROM USER_TABLES)
            UNION ALL
            SELECT 'DROP SEQUENCE ' || SEQUENCE_NAME FROM USER_SEQUENCES
             WHERE SEQUENCE_NAME IN ('TH_LEVEL_SEQ','TH_SCENE_SEQ','TH_OBJ_SEQ','TH_PAL_SEQ','TH_STRIKE_SEQ','TH_VIEW_SEQ'))
  LOOP
    EXECUTE IMMEDIATE r.stmt;
  END LOOP;
END;
/
