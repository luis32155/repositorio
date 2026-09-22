\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA core;
CREATE TABLE core.fsd001 (pepais integer, petdoc integer, pendoc text, petipo text);
CREATE TABLE core.fsd002 (pfpais integer, pftdoc integer, pfndoc text, pfnom1 text, pfnom2 text, pfape1 text, pfape2 text, pfcant text, pffnac text, pfeciv text);
CREATE TABLE core.fsd003 (pjpais integer, pjtdoc integer, pjndoc text, pjrazs text, pjfcon text);
CREATE TABLE core.lwvd50a (wv50apais integer, wv50atdoc integer, wv50andoc text, wv50atdat text, wv50adcon text, wv50aest text);
CREATE TABLE core.fsr008 (pepais integer, petdoc integer, pendoc text, pgcod integer, ctnro integer);
CREATE TABLE core.fsd008 (pgcod integer, ctnro integer, ctnom text);
CREATE TABLE core.lcpd18 (cpd17codre integer, cpd18pguni integer, cpd18ctuni integer, cpd18pgcod integer, cpd18ctnro integer);

-- Synthetic fixtures; X/Y and relationship codes are NOT proposed business mappings.
INSERT INTO core.fsd001 VALUES (604,1,'SYN000000001','X'),(604,2,'SYN000000002','Y'),(604,1,'SYN000000003','X');
INSERT INTO core.fsd002 VALUES (604,1,'SYN000000001','Ana ',NULL,'Prueba ','Datos','1','19900102','3');
INSERT INTO core.fsd003 VALUES (604,2,'SYN000000002','Empresa de prueba','20000101');
INSERT INTO core.lwvd50a VALUES
 (604,1,'SYN000000001','1','one@example.test','1'),
 (604,1,'SYN000000001','1','two@example.test','1'),
 (604,1,'SYN000000001','1','one@example.test','1'),
 (604,1,'SYN000000001','2','900000001','1'),
 (604,1,'SYN000000001','2','900000002','0');
INSERT INTO core.fsr008 VALUES (604,1,'SYN000000001',1,111111111),(604,1,'SYN000000001',1,222222222);
INSERT INTO core.fsd008 VALUES (1,111111111,'Cuenta uno'),(1,222222222,'Cuenta dos');
INSERT INTO core.lcpd18 VALUES (10,1,333333333,1,111111111),(20,1,444444444,1,111111111),(10,1,555555555,1,222222222);
