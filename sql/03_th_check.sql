-- ============================================================================
--  Thunder · Oracle 21c · Service PDBORCL · Schema DEMO
--  03 – Kontrolle nach der Installation
-- ============================================================================

-- Erwartet: TH_STORM_LEVEL = 3, TH_SCENE = 1, TH_SCENE_OBJECT = 7, TH_PALETTE = 34 (je Stimmung 17)
SELECT 'TH_STORM_LEVEL' AS TABELLE, COUNT(*) AS ANZAHL, 3 AS ERWARTET FROM TH_STORM_LEVEL
UNION ALL SELECT 'TH_SCENE',        COUNT(*), 1  FROM TH_SCENE
UNION ALL SELECT 'TH_SCENE_OBJECT', COUNT(*), 7  FROM TH_SCENE_OBJECT
UNION ALL SELECT 'TH_PALETTE',      COUNT(*), 34 FROM TH_PALETTE;

-- Stufen im Überblick
SELECT SORT_ORDER, CODE, NAME_DE, RATE_SEC, SHARE_IC, SHARE_CROWN,
       1 - SHARE_IC - SHARE_CROWN AS SHARE_CG, DIST_MIN_KM, DIST_MAX_KM, RAIN_DROPS, WIND, EMBER
  FROM TH_STORM_LEVEL ORDER BY SORT_ORDER;

-- Jede Farbrolle muss in beiden Stimmungen vorkommen (erwartet: keine Zeilen)
SELECT ROLE, COUNT(*) AS STIMMUNGEN FROM TH_PALETTE GROUP BY ROLE HAVING COUNT(*) <> 2;

-- Objekte der Szene
SELECT o.SORT_ORDER, o.OBJ_TYPE, o.POS_X, o.DEPTH, o.OBJ_SCALE, o.COLOR_HEX
  FROM TH_SCENE_OBJECT o JOIN TH_SCENE s ON s.SCENE_ID = o.SCENE_ID
 WHERE s.CODE = 'OSTSEE' ORDER BY o.SORT_ORDER;

-- Nach dem ersten App-Lauf: Blitzprotokoll
SELECT * FROM TH_STRIKE_DAY_V ORDER BY TAG DESC, STUFE;
