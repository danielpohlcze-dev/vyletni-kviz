# Oprava přípravy kvízu 2.6 — 13. 9. 2026

## Co bylo opraveno

Původní kód spojoval všechny nedokončené serverové odpovědi do stejné hlášky. Snímek z telefonu neobsahuje skutečný serverový důvod, proto z něj nelze spolehlivě určit, zda šlo o limit výstupu, chybu úlohy, nebo jiný stav.

Nová verze rozlišuje tyto stavy. Jen při potvrzeném `incomplete / max_output_tokens` zmenší skupinu 5 → 2 → 1; zachová model, reasoning i limity. Kontrola pracuje nad uloženými návrhy a po zmenšení je znovu negeneruje. Opravy špatného formátu kontroly také zachovají návrh. Po vyčerpání rozumné obnovy se příprava zastaví. Aplikace ukazuje potvrzený stav úlohy a uloží posledních 100 diagnostických událostí.

## Ověření

Zdrojový commit: `b6d8160d4a15b4080d4062f2e137a96c33b5b6c5`.
[Úspěšné sestavení a testy](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34741822976).

| Oblast | Testů | Výsledek |
| --- | ---: | --- |
| Původní generátor | 11 | prošly |
| Nové scénáře obnovy | 10 | prošly |
| Síťová vrstva | 6 | prošly |
| Historie | 4 | prošly |
| Android emulátor, API 35 | 17 | prošly |

XML byly staženy z artefaktů a zkontrolovány: žádná selhání, chyby ani přeskočení. Jsou uložené vedle této zprávy. Prošly také samostatné GameRules a 4 testy live harnessu. První sestavení opravy zastavila nekompatibilní JSON metoda v novém testu; po opravě testu prošlo výše uvedené sestavení. Tento neúspěch není zatajený ani přejmenovaný.

Regrese zahrnují 30 otázek po neúplné odpovědi, zachování návrhu při rozdělení kontroly, zastavení na jedné otázce, odmítnutí částečného JSON, zastavení při jiných terminálních stavech, restart z checkpointu, starý checkpoint, pořadí existující rozpracované kontroly a zachování důvodů odmítnutí.

Android testy zahrnují pět hráčů, osobní témata, předávání a hodnoty bodů, zpět/vpřed, rozehranou hru, dokončení 40 otázek, kroniku a tabulku, import a sdílecí dialog, skutečné vykreslení testovacího obrázku z URI, ukládání zdrojů a přesnou novou chybovou hlášku. Neověřují každou možnou interakci ani kompletní vizuální vzhled. Artefakt tohoto běhu neobsahuje snímky obrazovek.

## APK a aktualizace

[Instalační APK](../../../releases/VyletniKviz-2.6.0.apk) je tentýž kód, manifest a zdroje jako testovaná CI sestava; změněn je pouze podpis. Rovnost obsahů byla ověřena po jednotlivých souborech, kromě podpisových metadat.

- Verze: 2.6.0, versionCode 10, applicationId `cz.ctuprotebe.vyletnikviz.quality26`.
- SHA-256 APK: `e5e5e054b202ec892974c99bbcbf219b6404eb6a7abc6b5145036005c2f2b1b5`.
- SHA-256 certifikátu: `830ab6a1051bfe13e35fca889cba5e2d2aa5682ee88db44759bf28ceee0f27aa`.
- `apksigner verify` úspěšný, podpisy v2 a v3 platné.

Soukromý podpis a jeho záloha zůstávají mimo repozitář. Budoucí aktualizace této řady musí použít tentýž podpis, package a vyšší versionCode. Samotný CI debug artefakt s jiným podpisem aktualizací není. Jde stále o vývojové sestavení aplikace.

Protože podpis předchozí 2.5 není dostupný, tato řada se instaluje vedle ní. Původní aplikaci není nutné mazat. Její data se automaticky nepřenášejí: výlety lze ručně sdílet/importovat a AI připojení je potřeba nastavit v nové aplikaci. Přenos lokálních fotografií ani rozpracované přípravy mezi těmito oddělenými aplikacemi není zajištěn.

## Hranice výsledku

Nová 2.6 nebyla znovu testována proti placenému API. Oba povolené dodatečné živé běhy byly již využity a automatizace vypnuta. [Historický živý výsledek 10/10](../live-2026-09-12/REPORT.md) se týká předchozího kódu. Simulace 30 otázek neprokazuje živé dokončení 30 otázek na Danielově telefonu. Přesnou příčinu původního telefonního stavu nemáme z jeho snímku potvrzenou. Nová diagnostika ji při dalším výskytu rozliší. Žádná záruka stoprocentní faktické správnosti budoucích otázek z těchto testů neplyne.

