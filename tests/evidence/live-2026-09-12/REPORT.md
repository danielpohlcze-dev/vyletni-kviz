# Skutečný online test Výletního kvízu — 12. 9. 2026

Tato zpráva zachycuje verzi 2.5 před opravou opakované nedokončené odpovědi ve verzi 2.6. Není živým testem nové opravy.

Výsledek: dokončená sada 10/10 otázek, ale až navázáním druhého omezeného běhu. Nešlo o úspěšné vygenerování celé sady na první pokus. Model, obtížnost ani validační podmínky nebyly pro dosažení úspěchu sníženy.

## Průběh a náklady

| Běh | Výsledek | Čas samotného testu | POST | Vstupní / výstupní tokeny | Webová volání |
| --- | --- | --- | --- | --- | --- |
| [Dodatečný 1](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34720470223) | Neúspěch: BudgetExceeded, 5 přijatých + návrh dalších 5 | 329 s | 4 | 180 519 / 15 346 | 20 |
| [Dodatečný 2](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34720846787) | Úspěch: 10/10 | 163 s | 4 | 141 991 / 6 933 | 16 |
| Celkem za tyto dva běhy | Dokončená jedna desetiotázková sada | 492 s (8 min 12 s) | 8 | 322 510 / 22 279 | 36 |

Čas nezahrnuje přípravu CI, pauzu mezi běhy a následnou obsahovou kontrolu. Tokeny a počty volání jsou měření harnessu, ne výpis fakturace. Přesnou dolarovou cenu ani zbývající kredit tento test nezjišťuje. Model zůstal `gpt-5.6-sol`, reasoning `high`, nejvýše 4 POST na běh, 18 000 výstupních tokenů a 8 webových volání na požadavek.

První dodatečný běh zastavil výhradně testovací limit; výsledek se nesmí přejmenovat na úspěch ani na vyčerpaný kredit. Původní starší běh 34705609000 s výsledkem 7/10 není do součtu zahrnut: otázky tehdy nebyly archivovány, takže nebylo možné na ně navázat.

Druhý běh použil nezměněný checkpoint z prvního artefaktu. SHA-256 vstupního `resume.json`: `9e7cb69dcdb8e171be3730f7e400061809b566e3511b34cea8b92949fe8826a5`. Z pěti uložených návrhů nejprve přijal čtyři a otázku na slotu 8 nahradil. Prvních pět schválených otázek zůstalo datově totožných. Oba běhy úspěšně obnovily GET načítání po záměrně vyvolaném přerušení; opakování již odeslané úlohy nebylo potřeba. To nedokládá obnovení nejasného POST timeoutu bez získaného ID ani chování skutečné mobilní sítě.

## Kontrola finálního souboru

Provedeno nad staženým artefaktem, nejen nad zeleným stavem workflow:

- Přesně 10 jedinečných otázek a sloty 1–10 ve správném pořadí.
- Všechna jména, témata a příznaky obtížnosti odpovídají plánu.
- Každá otázka má čtyři rozdílné možnosti a platný index odpovědi.
- Finální sada odpovídá přijatým otázkám v závěrečném checkpointu.
- Prvních pět přijatých otázek je zachováno beze změny.
- Obsah všech deseti otázek, odpovědí a vysvětlení byl dodatečně porovnán s níže uvedenými zdroji. Nebyla nalezena chybná správná odpověď ani záměna tématu.

## Obsahová kontrola

| Slot / hráč | Obsah a odpověď | Kontrola zdroje a poznámka |
| --- | --- | --- |
| 1 / Barča | Zakládací listina pražské univerzity, 7. 4. 1348 — Karel IV. | [Univerzita Karlova](https://cuni.cz/UK-1391.html) dokládá datum, vydavatele i ochranu členů univerzity. |
| 2 / Dominik | Největší žijící tučňák — císařský | [Australian Antarctic Program](https://www.antarctica.gov.au/about-antarctica/animals/penguins/emperor-penguin/) dokládá prvenství a hmotnost do 40 kg na počátku rozmnožování. Počet 18 druhů je údaj tohoto zdroje, nikoli nadčasová shoda všech taxonomií. |
| 3 / Daneček | Izotop v definici sekundy — cesium-133 | [BIPM](https://www.bipm.org/en/si-base-units/second) dokládá izotop, přechod i přesnou frekvenci. Odbornější, ale jednoznačná otázka. |
| 4 / Barča | Český rap, Kato + DJ Skupla — Prago Union | [Oficiální biografie](https://www.pragounion.cz/bio/) dokládá vznik kolem roku 2002 i Katovo působení v Chaozz. Skutečný obsah odpovídá rapovému tématu. |
| 5 / Dominik | Jmenování slovenského premiéra 1. 4. 2021 — Eduard Heger | [Oficiální prezidentský archiv](https://archiv.prezident.sk/zuzana-caputova/article/prezidentka-vymenovala-vladu-edurada-hegera/) dokládá i dohodu čtyř koaličních stran. Původní URL v artefaktu neposkytlo při této kontrole čitelný obsah; tentýž článek byl dohledán v oficiálním archivu. |
| 6 / Daneček | Význam „equivocal“ v kontextu výsledků výzkumu — nejednoznačné / neprůkazné | [Collins](https://www.collinsdictionary.com/us/dictionary/english/equivocal) podporuje tento kontextový význam. Kontext v zadání také usnadňuje odvození odpovědi; označení „těžká“ je redakční úsudek. |
| 7 / Barča | Blood Falls — sloučeniny železa ve slané vodě | [NASA](https://science.nasa.gov/earth/earth-observatory/blood-falls-antarcticas-dry-valleys-35535/) a [výzkumná studie](https://www.frontiersin.org/journals/astronomy-and-space-sciences/articles/10.3389/fspas.2022.843174/full) podporují odpověď. Studie upřesňuje oxidaci rozpuštěného železa a amorfní částice; obecné znění otázky netvrdí konkrétní nesprávný minerál. |
| 8 / Dominik | Pohoří se Sněžkou — Krkonoše | [ČSÚ](https://csu.gov.cz/hkk/strucna_charakteristika_kraje) dokládá pohoří, prvenství i výšku 1 603 m použitou ve vysvětlení. Velmi lehká otázka. |
| 9 / Daneček | Anatomické dílo z roku 1543 — Andreas Vesalius | [National Library of Medicine](https://www.nlm.nih.gov/exhibition/historicalanatomies/vesalius_bio.html) dokládá autora, rok, sedm knih i vlastní pitvy a přehodnocení Galénových tvrzení. |
| 10 / Barča | Slovanská epopej — Alfons Mucha | [GHMP](https://www.ghmp.cz/vystavy/alfons-mucha-slovanska-epopej/) dokládá autora, dvacet obrazů a roky 1912–1926. |

## Neplacené testy a hranice výsledku

[Android a unit testy](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34720470216) prošly. Stažené XML obsahuje 16 Android instrumentačních testů a 20 unit testů (11 generátor, 5 síť, 4 historie), všechny bez chyby a bez přeskočení. Prošly také samostatné GameRules a testy live harnessu. Následující [sestavení](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34720846769) rovněž prošlo.

Tento důkaz není end-to-end testem živého API na fyzickém telefonu. Živý harness používá produkční generátor a síťovou vrstvu v CI; Android testy jsou samostatné. Placená sada má 10 otázek pro tři testovací hráče, nikoli celých 40 otázek pro pět lidí. Zátěž, skutečné odpojení mobilní sítě, fotoaparát, fyzické zvuky/vibrace a přenos fotek mezi dvěma telefony tímto ověřeny nejsou.

Zůstávají další omezení: zdroje mohou změnit adresu, obtížnost není dokonale vyvážená a shoda dvou průchodů stejného modelu není zárukou pravdivosti všech budoucích otázek. `quality_feedback` uchovává jen poslední stav; po následném úspěchu je prázdný, takže přesné důvody všech dřívějších odmítnutí z těchto artefaktů zpětně nezjistíme. Vhodným dalším vylepšením je trvalá historie jednotlivých validačních rozhodnutí bez citlivých dat.

Oba povolené dodatečné placené běhy byly využity. Další automatické spouštění bylo po dokončení vypnuto. Nebyl měněn model, limity, produkční data telefonu ani API klíč. Žádný kredit nebyl dobíjen.

## Uložené důkazy

`quiz.json`, `summary-followup-1.json` a `summary-followup-2.json` jsou nezměněné výstupy stažených artefaktů. SHA-256 finálního `quiz.json`: `5e6c697c90650384a37d5fe4b71ebc4905e72bfd4df87d6b248fea9ac7e09408`. Checkpoint pro druhý běh je v historii commitu `1627b07737f9df05841c2657adffc43ee7b5fa38`; není to uložená skutečná hra uživatele.

