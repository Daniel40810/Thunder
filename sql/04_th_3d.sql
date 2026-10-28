-- ============================================================================
--  Thunder · Oracle 21c · Service PDBORCL · Schema DEMO
--  04 – Thunder in 3D (Sylt-Werkbuch Phase 18c, F52 A). Wiederholbar: legt an, was fehlt, und setzt die Werte neu.
--
--  · TH_SCENE_OBJECT bekommt die Lage in Metern: X_M (Ost), Z_M (Süd), ROT_DEG (Grad von Nord).
--    Ursprung ist der Standpunkt des Hauptblicks auf der Düne, 0 m = Mittelwasser.
--  · TH_VIEW hält die Ansichten auf den Tasten 1 bis 5.
--  · TH_STRIKE bekommt den Ort jedes Blitzes in Metern (X_M, Z_M) und seine Art in com.dan.lightning (BOLT_KIND).
--  · TH_STRIKE_ORT_V zeigt die Blitze mit Peilung und Abstand vom Hauptblick.
--
--  Dieselben Zahlen stecken als Rückfall in der App (scene.Lage, camera.Ansichten); tools.Pruefung vergleicht sie.
--  Ausführen als DEMO: in SQL Developer mit F5, oder  sqlplus DEMO@//localhost:1521/PDBORCL @sql/04_th_3d.sql
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON

-- ---------------------------------------------------------------- Spalten und Tabelle (nur, was fehlt)
DECLARE
  PROCEDURE ddl(p_sql VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
  END;

  FUNCTION hat_spalte(p_tab VARCHAR2, p_col VARCHAR2) RETURN BOOLEAN IS
    n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO n FROM USER_TAB_COLUMNS WHERE TABLE_NAME = p_tab AND COLUMN_NAME = p_col;
    RETURN n > 0;
  END;

  FUNCTION hat(p_typ VARCHAR2, p_name VARCHAR2) RETURN BOOLEAN IS
    n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO n FROM USER_OBJECTS WHERE OBJECT_TYPE = p_typ AND OBJECT_NAME = p_name;
    RETURN n > 0;
  END;
BEGIN
  -- Lage der Objekte in Metern
  IF NOT hat_spalte('TH_SCENE_OBJECT', 'X_M') THEN
    ddl('ALTER TABLE TH_SCENE_OBJECT ADD (X_M NUMBER(8,2))');
  END IF;
  IF NOT hat_spalte('TH_SCENE_OBJECT', 'Z_M') THEN
    ddl('ALTER TABLE TH_SCENE_OBJECT ADD (Z_M NUMBER(8,2))');
  END IF;
  IF NOT hat_spalte('TH_SCENE_OBJECT', 'ROT_DEG') THEN
    ddl('ALTER TABLE TH_SCENE_OBJECT ADD (ROT_DEG NUMBER(5,1) CONSTRAINT TH_OBJ_ROT_CK CHECK (ROT_DEG >= 0 AND ROT_DEG < 360))');
  END IF;

  -- Ort der Blitze
  IF NOT hat_spalte('TH_STRIKE', 'X_M') THEN
    ddl('ALTER TABLE TH_STRIKE ADD (X_M NUMBER(9,1))');
  END IF;
  IF NOT hat_spalte('TH_STRIKE', 'Z_M') THEN
    ddl('ALTER TABLE TH_STRIKE ADD (Z_M NUMBER(9,1))');
  END IF;
  IF NOT hat_spalte('TH_STRIKE', 'BOLT_KIND') THEN
    ddl('ALTER TABLE TH_STRIKE ADD (BOLT_KIND VARCHAR2(12) CONSTRAINT TH_STRIKE_BOLT_CK '
        || 'CHECK (BOLT_KIND IN (''CLOUD_GROUND'',''INTRA_CLOUD'',''SPIDER'',''SHEET'')))');
  END IF;

  -- Ansichten
  IF NOT hat('TABLE', 'TH_VIEW') THEN
    ddl('CREATE TABLE TH_VIEW (
           VIEW_ID      NUMBER(6)          CONSTRAINT TH_VIEW_PK PRIMARY KEY,
           SCENE_ID     NUMBER(4)          NOT NULL CONSTRAINT TH_VIEW_SCENE_FK REFERENCES TH_SCENE (SCENE_ID) ON DELETE CASCADE,
           KEY_NO       NUMBER(1)          NOT NULL CONSTRAINT TH_VIEW_KEY_CK CHECK (KEY_NO BETWEEN 1 AND 9),
           NAME_DE      VARCHAR2(30 CHAR)  NOT NULL,
           X_M          NUMBER(8,2)        NOT NULL,
           Z_M          NUMBER(8,2)        NOT NULL,
           ABOVE_M      NUMBER(6,2)        NOT NULL CONSTRAINT TH_VIEW_ABOVE_CK CHECK (ABOVE_M BETWEEN 0.3 AND 2000),
           REFERENCE    VARCHAR2(5)        DEFAULT ''GRUND'' NOT NULL CONSTRAINT TH_VIEW_REF_CK CHECK (REFERENCE IN (''GRUND'',''DECK'')),
           HEADING_DEG  NUMBER(5,1)        NOT NULL CONSTRAINT TH_VIEW_HEAD_CK CHECK (HEADING_DEG >= 0 AND HEADING_DEG < 360),
           PITCH_DEG    NUMBER(4,1)        NOT NULL CONSTRAINT TH_VIEW_PITCH_CK CHECK (PITCH_DEG BETWEEN -89 AND 89),
           FOVY_DEG     NUMBER(4,1)        DEFAULT 50 NOT NULL CONSTRAINT TH_VIEW_FOVY_CK CHECK (FOVY_DEG BETWEEN 10 AND 120),
           DESCRIPTION  VARCHAR2(200 CHAR),
           CONSTRAINT TH_VIEW_KEY_UK UNIQUE (SCENE_ID, KEY_NO)
         )');
    ddl('CREATE INDEX TH_VIEW_SCENE_IX ON TH_VIEW (SCENE_ID)');
    DBMS_OUTPUT.PUT_LINE('TH_VIEW angelegt');
  END IF;
  IF NOT hat('SEQUENCE', 'TH_VIEW_SEQ') THEN
    ddl('CREATE SEQUENCE TH_VIEW_SEQ START WITH 1 INCREMENT BY 1 NOCACHE');
  END IF;
  ddl('CREATE OR REPLACE TRIGGER TH_VIEW_BI BEFORE INSERT ON TH_VIEW FOR EACH ROW
       BEGIN
         IF :NEW.VIEW_ID IS NULL THEN :NEW.VIEW_ID := TH_VIEW_SEQ.NEXTVAL; END IF;
       END;');
END;
/

COMMENT ON COLUMN TH_SCENE_OBJECT.X_M     IS 'seit 3D: Lage in m nach Osten vom Standpunkt des Hauptblicks';
COMMENT ON COLUMN TH_SCENE_OBJECT.Z_M     IS 'seit 3D: Lage in m nach Süden vom Standpunkt des Hauptblicks';
COMMENT ON COLUMN TH_SCENE_OBJECT.ROT_DEG IS 'seit 3D: Richtung in Grad von Nord (Strandkorb: Öffnung, Seebrücke: Kurs zum Kopf, Düne: Hauptblick)';
COMMENT ON TABLE  TH_VIEW                 IS 'Ansichten der 3D-Szene auf den Tasten 1 bis 9';
COMMENT ON COLUMN TH_VIEW.ABOVE_M         IS 'Augenhöhe über dem Bezug in m';
COMMENT ON COLUMN TH_VIEW.REFERENCE       IS 'Bezug der Augenhöhe: GRUND (Gelände, über Wasser 0 m) oder DECK (Seebrücke, 3,2 m)';
COMMENT ON COLUMN TH_VIEW.PITCH_DEG       IS 'Neigung des Blicks in Grad, positiv nach unten';
COMMENT ON COLUMN TH_VIEW.FOVY_DEG        IS 'Öffnungswinkel senkrecht in Grad';
COMMENT ON COLUMN TH_STRIKE.X_M           IS 'seit 3D: Einschlag (Erdblitz) bzw. Mitte der Entladung in m nach Osten vom Hauptblick';
COMMENT ON COLUMN TH_STRIKE.Z_M           IS 'seit 3D: dasselbe in m nach Süden';
COMMENT ON COLUMN TH_STRIKE.BOLT_KIND     IS 'seit 3D: Art in com.dan.lightning: CLOUD_GROUND, INTRA_CLOUD, SPIDER, SHEET';

-- ---------------------------------------------------------------- Werte (Szene OSTSEE, nach SORT_ORDER aus 02_th_data.sql)
MERGE INTO TH_SCENE_OBJECT o
USING (SELECT s.SCENE_ID, v.SORT_ORDER, v.X_M, v.Z_M, v.ROT_DEG
         FROM TH_SCENE s
        CROSS JOIN (SELECT 1 SORT_ORDER,  83.16 X_M, -391.26 Z_M,   0.0 ROT_DEG FROM DUAL UNION ALL   -- Leuchtturm
                    SELECT 2,             15.56,       2.83,        66.1         FROM DUAL UNION ALL   -- Seebrücke (Wurzel)
                    SELECT 3,              0.00,       0.00,        45.0         FROM DUAL UNION ALL   -- Düne (Hauptblick)
                    SELECT 4,             11.95,     -15.63,       225.4         FROM DUAL UNION ALL   -- Strandkorb blau
                    SELECT 5,             12.59,     -12.16,       220.0         FROM DUAL UNION ALL   -- Strandkorb grün
                    SELECT 6,              8.06,     -13.15,       219.5         FROM DUAL UNION ALL   -- Strandkorb blau
                    SELECT 7,             10.47,      -7.21,       229.4         FROM DUAL) v           -- Strandkorb rot
        WHERE s.CODE = 'OSTSEE') n
   ON (o.SCENE_ID = n.SCENE_ID AND o.SORT_ORDER = n.SORT_ORDER)
 WHEN MATCHED THEN UPDATE SET o.X_M = n.X_M, o.Z_M = n.Z_M, o.ROT_DEG = n.ROT_DEG;

MERGE INTO TH_VIEW t
USING (SELECT s.SCENE_ID, v.*
         FROM TH_SCENE s
        CROSS JOIN (SELECT 1 KEY_NO, 'Hauptblick' NAME_DE,    0.00 X_M,    0.00 Z_M,  1.60 ABOVE_M, 'GRUND' REFERENCE, 45.0 HEADING_DEG, -5.3 PITCH_DEG, 50.0 FOVY_DEG,
                           'Vom Dünenkamm über die Strandkörbe nach Nordost, wie die fünf Bilder des 2D-Thunder' DESCRIPTION FROM DUAL UNION ALL
                    SELECT 2, 'Seebrücke',  148.17,  -55.90,  1.70, 'DECK',  58.1, -1.5, 50.0, 'Auf dem Deck, gut halb draußen, Blick zum Kopf' FROM DUAL UNION ALL
                    SELECT 3, 'Leuchtturm', -23.33, -313.25,  1.70, 'GRUND', 53.8, -6.0, 50.0, 'Auf der Landzunge, Blick hinauf zum Turm' FROM DUAL UNION ALL
                    SELECT 4, 'Strandkorb',  12.45,   -6.93,  1.10, 'GRUND', 35.0, -2.0, 50.0, 'Sitzhöhe neben dem roten Korb' FROM DUAL UNION ALL
                    SELECT 5, 'Drohne',    -106.07,   49.50, 55.00, 'GRUND', 50.0,  9.0, 50.0, 'Hinter der Düne in 55 m, Blick über Strand und Bucht' FROM DUAL) v
        WHERE s.CODE = 'OSTSEE') n
   ON (t.SCENE_ID = n.SCENE_ID AND t.KEY_NO = n.KEY_NO)
 WHEN MATCHED THEN UPDATE SET t.NAME_DE = n.NAME_DE, t.X_M = n.X_M, t.Z_M = n.Z_M, t.ABOVE_M = n.ABOVE_M, t.REFERENCE = n.REFERENCE,
                              t.HEADING_DEG = n.HEADING_DEG, t.PITCH_DEG = n.PITCH_DEG, t.FOVY_DEG = n.FOVY_DEG, t.DESCRIPTION = n.DESCRIPTION
 WHEN NOT MATCHED THEN INSERT (SCENE_ID, KEY_NO, NAME_DE, X_M, Z_M, ABOVE_M, REFERENCE, HEADING_DEG, PITCH_DEG, FOVY_DEG, DESCRIPTION)
                       VALUES (n.SCENE_ID, n.KEY_NO, n.NAME_DE, n.X_M, n.Z_M, n.ABOVE_M, n.REFERENCE, n.HEADING_DEG, n.PITCH_DEG, n.FOVY_DEG, n.DESCRIPTION);

COMMIT;

-- ---------------------------------------------------------------- Auswertung: Blitze mit Peilung und Abstand
CREATE OR REPLACE VIEW TH_STRIKE_ORT_V AS
SELECT s.STRIKE_ID, s.STRUCK_AT, NVL(l.NAME_DE, '–') AS STUFE, s.MOOD, s.KIND, s.BOLT_KIND, s.ORIGIN, s.PEAK_KA,
       s.X_M, s.Z_M,
       ROUND(MOD(ATAN2(s.X_M, -s.Z_M) * 180 / ACOS(-1) + 360, 360), 1) AS PEILUNG_DEG,
       ROUND(SQRT(s.X_M * s.X_M + s.Z_M * s.Z_M) / 1000, 2)              AS ABSTAND_KM,
       s.DIST_KM AS REGIE_KM
  FROM TH_STRIKE s
  LEFT JOIN TH_STORM_LEVEL l ON l.LEVEL_ID = s.LEVEL_ID
 WHERE s.X_M IS NOT NULL AND s.Z_M IS NOT NULL;
COMMENT ON TABLE TH_STRIKE_ORT_V IS 'Blitze der 3D-App mit Peilung (Grad von Nord) und Abstand vom Hauptblick; REGIE_KM = Entfernung, die der StormDirector wollte';

-- ---------------------------------------------------------------- Kontrolle (erwartet: 7 Objekte mit Lage, 5 Ansichten)
SELECT (SELECT COUNT(*) FROM TH_SCENE_OBJECT WHERE X_M IS NOT NULL AND Z_M IS NOT NULL AND ROT_DEG IS NOT NULL) AS OBJEKTE_MIT_LAGE,
       7 AS ERWARTET_OBJEKTE,
       (SELECT COUNT(*) FROM TH_VIEW) AS ANSICHTEN,
       5 AS ERWARTET_ANSICHTEN
  FROM DUAL;
