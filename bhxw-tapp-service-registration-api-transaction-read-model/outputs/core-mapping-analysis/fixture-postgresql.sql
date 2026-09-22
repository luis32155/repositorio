\set ON_ERROR_STOP on
INSERT INTO core.fsd001 (pepais, petdoc, pendoc, petipo, penom, pecuebt) VALUES
    (604, 1, 'SYN000000001', 'F', 'Persona uno', 111111111),
    (604, 2, 'SYN000000002', 'J', 'Empresa dos', 222222222),
    (604, 1, 'SYN000000003', 'F', 'Persona tres', NULL);
INSERT INTO core.fsd002
    (pfpais, pftdoc, pfndoc, pfnom1, pfnom2, pfape1, pfape2, pfcant, pffnac, pfeciv)
VALUES (604, 1, 'SYN000000001', 'Ana ', NULL, 'Prueba ', 'Datos', '1', DATE '1990-01-02', '3');
INSERT INTO core.fsd003 (pjpais, pjtdoc, pjndoc, pjrazs, pjfcon)
VALUES (604, 2, 'SYN000000002', 'Empresa de prueba', DATE '2000-01-01');
INSERT INTO core.lwvd50a
    (wv50apais, wv50atdoc, wv50andoc, wv50atdat, wv50adcon, wv50aest)
VALUES
    (604, 1, 'SYN000000001', '1', 'one@example.test', '1'),
    (604, 1, 'SYN000000001', '1', 'two@example.test', '1'),
    (604, 1, 'SYN000000001', '1', 'one@example.test', '1'),
    (604, 1, 'SYN000000001', '2', '900000001', '1'),
    (604, 1, 'SYN000000001', '2', '900000002', '0');
INSERT INTO core.fsd008 (pgcod, ctnro, ctnom)
VALUES (1, 111111111, 'Cuenta uno'), (1, 222222222, 'Cuenta dos');
INSERT INTO core.fsr008 (pepais, petdoc, pendoc, pgcod, ctnro)
VALUES (604, 1, 'SYN000000001', 1, 111111111),
       (604, 1, 'SYN000000001', 1, 222222222);
INSERT INTO core.lcpd18
    (cpd17codre, cpd18pguni, cpd18ctuni, cpd18pgcod, cpd18ctnro)
VALUES (10, 1, 333333333, 1, 111111111),
       (20, 1, 444444444, 1, 111111111),
       (10, 1, 555555555, 1, 222222222);

