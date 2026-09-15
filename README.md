# Výletní kvíz

**Stav po auditu 14. 9. 2026:** aplikace zatím nesplňuje celé původní zadání. Známé mezery zahrnují nastavení offline okruhů/obtížnosti, férové rozdělení bizarních otázek, přenos fotek a rozehrané hry mezi telefony a požadovanou serverovou architekturu AI. [Konkrétní zjištění a nové testy distribuované APK](tests/evidence/audit-2.6/REPORT.md).

[Stáhnout Výletní kvíz 2.6.1 pro Android](https://github.com/danielpohlcze-dev/vyletni-kviz/raw/refs/heads/main/releases/VyletniKviz-2.6.1.apk) · [Finální test přesně distribuovaného APK](tests/evidence/release-2.6.1/REPORT.md)

## Oprava přípravy kvízu ve verzi 2.6

Původní dialog nerozlišoval `incomplete`, `failed` ani `cancelled`; snímek obrazovky proto sám neprokazuje příčinu. Nová verze rozlišuje bezpečný stav a důvod ukončení. Při `incomplete_details.reason=max_output_tokens` zmenší skupinu z pěti na dvě a následně na jednu otázku. Nemění model Sol/high, tokenový limit ani kontrolu zdrojů. Uložený návrh při neúplné kontrole zachová a kontroluje po částech. Po neúspěchu i s jednou otázkou se zastaví. Ostatní terminální chyby nevyvolají automatické placené opakování.

Na obrazovce se rozlišují schválené otázky, hotový návrh a skutečný stav serverové úlohy. Checkpoint obsahuje posledních 100 diagnostických událostí bez klíčů a syrových chybových odpovědí. Při nejasném POST timeoutu se zachovává ochrana proti automatickému opakování.

Podklady: [OpenAI — limit délky a reasoning](https://developers.openai.com/api/docs/guides/reasoning), [background režim](https://developers.openai.com/api/docs/guides/background).

**Aktuální online kontrola 15. 9. 2026:** skutečné API dokončilo kontrolovanou sadu 10/10. Test navázal z pěti hotových otázek, bezpečně obnovil simulovaně přerušený polling a k nové pětici použil dva POST požadavky (autor + kontrola). [Otázky, spotřeba, zdroje a omezení závěru](tests/evidence/live-2026-09-15/REPORT.md). Předchozí stav `credit_balance_exhausted` je zachovaný v [historickém záznamu](tests/evidence/live-2026-09-14/REPORT.md).

Historická verze 2.5 dokončila sadu 10/10 po dvou omezených placených bězích; první skončil na testovacím limitu, druhý navázal z checkpointu. Výsledek, spotřeba a kontrola všech zdrojů jsou v [zprávě s důkazy](tests/evidence/live-2026-09-12/REPORT.md). Produkční generátor a transport verze 2.6 nyní prošly novým placeným testem 10/10; stále však nejde o živý test třiceti otázek přímo na fyzickém telefonu.

Android aplikace pro 2–5 pojmenovaných hráčů u jednoho telefonu. Pevné pořadí, přebírání otázek, body, kronika výletů a fotografie. Připravená hra funguje offline.

Při „Neví“ se náhodně vyřadí chybná možnost, dokud zbývají alespoň dvě. Další hráči mohou otázku převzít za nižší bodovou hodnotu; správná možnost se nikdy automaticky nevyřazuje. Opakované zpracování stejné odpovědi ani výsledků nesmí připsat body nebo historii dvakrát. Import kontroluje strukturu záznamu a stejný identifikátor výletu znovu nezapočítává.

## AI příprava

- GPT-5.6 Sol, reasoning high, Responses API; 10/20/30/40 otázek po skupinách nejvýše pěti.
- Plán předem určuje hráče, téma a obtížnost každého slotu. Silné téma má přednost před obecnými kategoriemi.
- Autor musí použít webové vyhledávání. Zdroj je URL, název a stručné vysvětlení, který fakt podporuje.
- Samostatný kontrolní požadavek dostává otázky bez autorova indexu správné odpovědi. Znovu hledá zdroje, určí odpověď a hodnotí soulad tématu, jednoznačnost, obtížnost a celé vysvětlení.
- Aplikace kontroluje dokončený stav odpovědi, použití webového nástroje, příslušnost URL do skutečně vrácených zdrojů, čtyři různé možnosti, pořadí, duplicitní otázky a shodu obou odpovědí. Samotné modelové `verified: true` neakceptuje.
- Zamítnuté otázky se nahrazují nejvýše dvěma opravnými průchody. Přijaté zůstávají uložené; neúplná sada se automaticky nespouští.
- Odkazy jsou dostupné po odhalení odpovědi a v kronice; metadata se zachovají i v uložené hře a exportu.

Vyhledaný odkaz a souhlas dvou modelových průchodů nejsou důkaz absolutní faktické správnosti. Oba průchody používají stejný model v oddělených požadavcích. Automatické testy používají simulované odpovědi API a nejsou hodnocením faktické kvality živě generovaných otázek.

## Obnova a připojení

`QuizGeneration` obsahuje pravidla tvorby a validace, `OpenAiTransport` síť a obnovu Responses úloh, `MainActivity` obrazovky a ukládání. Příprava má samostatný záznam `ai_pending`, oddělený od rozehrané hry a historie. Ukládá kontext výletu, pevný plán, přijaté otázky, čekající návrh a ID odeslané úlohy. Při návratu se nejdříve načte existující odpověď přes GET, nikoli další POST.

DNS chyby mají nejvýše dva automatické opakované pokusy. Nejasný timeout odeslaného POST se automaticky neopakuje, protože mohl být již účtován. Pokud se ID odpovědi nestihlo vrátit a uložit, přesné obnovení této jediné úlohy není možné. Pozastavení zastaví další práci telefonu; již odeslaná serverová úloha může doběhnout. `background: true, store: true` umožňuje později načíst výsledek podle retenčních pravidel OpenAI.

API klíč uživatel zadává v telefonu; je zašifrovaný Android Keystore a nevkládá se do repozitáře ani testů. Aktuální osobní APK volá OpenAI přímo. Nový postup je dražší než původní rychlé generování; nemá automatický přechod na levnější model.

## Ověření

GitHub Actions spouští testy herních pravidel, `gradle testQualityUnitTest assembleQuality` a Android 35 instrumentační testy `connectedQualityAndroidTest`. Výsledky jsou samostatné artefakty. Testy generování zahrnují 40 slotů, opravu tématu, nesouhlas správné odpovědi, vymyšlené zdroje, neúplný výstup, duplicitní možnosti, obnovu po pádu procesu a oddělení síťové chyby od kvality kvízu.

Fotografický test ověřuje odeslání intentu pro Android picker se simulovaným zrušením; nedokládá skutečné pořízení fotky. Obrazovky respektují systémové lišty Androidu 15. Espresso testy obrazovek používají skutečná klepnutí; rozsáhlejší herní scénáře navíc přímo volají logiku Activity.

Rozšířené Android scénáře hrají celou 40otázkovou hru, zkoušejí hodnoty přebírání pro 2–5 hráčů, obnovení Activity, zpět/vpřed, opakované události, import a deduplikaci, sdílecí intent se zrušením, šifrování testovacího klíče a vykreslení skutečného testovacího obrázku přes Android MediaStore. Tyto testy neodesílají zprávy jiným lidem ani nevolají placené OpenAI API. Fotoaparát, fyzické zvuky/vibrace a přenos fotek mezi dvěma skutečnými telefony vyžadují ruční ověření.

Verze 2.6 používá applicationId `cz.ctuprotebe.vyletnikviz.quality26` a název „Výletní kvíz 2.6“. Instaluje se vedle 2.5, protože její dočasný CI podpis není dostupný. Starou aplikaci neodinstalovávejte před přenesením dat. Přenos výletů je ruční přes Sdílet tento výlet / Importovat sdílený výlet, API připojení se nastaví znovu. Fotografie uložené jako lokální URI nejsou přenosnou zálohou obrazových souborů. CI artefakt je vývojová sestava; distribuce s trvalým neveřejným podpisem probíhá samostatně. Soukromý podpisový klíč se nesmí uložit do repozitáře.

## Volitelný placený test skutečného API

1. V [novém GitHub Actions secretu](https://github.com/danielpohlcze-dev/vyletni-kviz/settings/secrets/actions/new) vyplňte Name `OPENAI_API_KEY`, vložte klíč do Secret a zvolte Add secret. Klíč nepatří do chatu, souboru v repozitáři ani snímku obrazovky.
2. Otevřete [Živý test AI — 10 otázek](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/workflows/live-quiz.yml), zvolte Run workflow na větvi main. Alternativně jej lze spustit výslovnou změnou `tests/live-trigger.txt`.
3. Výsledkem je artefakt `live-quiz-result`: skutečné otázky se zdroji a souhrn HTTP stavu, doby, tokenů a počtu požadavků. Klíč ani libovolné texty výjimek se nezapisují. Bez secretu se nic placeného neodešle. Běžné sestavení APK placený test nespouští.

Program kompiluje přímo produkční `QuizGeneration` a `OpenAiTransport`. Zkouší i obnovu po jednom simulovaném přerušení pollingu skutečné serverové úlohy. Pro omezení nákladů povoluje nejvýše čtyři pokusy POST (autor + kontrola pro dvě skupiny po pěti), 18 000 výstupních tokenů a navíc osm webových volání na požadavek. Pokud je potřeba více oprav, test skončí neúspěchem a vykáže přijatý počet otázek. Limity nejsou pevným dolarovým rozpočtem; účtování probíhá z vloženého OpenAI kreditu. Nejde o dlouhodobý zátěžový test ani o test mobilní sítě. Výstup je třeba obsahově zkontrolovat, úspěšný běh negarantuje bezchybnost každé budoucí otázky.

Test průběžně ukládá také `resume.json` a `accepted-questions.json` do svého artefaktu. Pro navázání lze přesný checkpoint posledního běhu umístit do `live-test/resume.json`; plán musí odpovídat testovací sadě. Jde výhradně o testovací otázky a metadata bez API klíče. První živý běh 34705609000 ještě tuto archivaci neměl: přijal 7/10 otázek za čtyři požadavky a úspěšně obnovil přerušený polling, potom skončil na testovacím limitu `BudgetExceeded`. Nešlo o chybu kreditu. Sedm otázek z tohoto prvního běhu se do artefaktu neuložilo, proto je nelze z něj obnovit. Další běhy již poskytují checkpoint a konkrétní důvody zamítnutí pro kontrolu.

Dokumentace API: [model](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [webové zdroje](https://developers.openai.com/api/docs/guides/tools-web-search), [úlohy na pozadí](https://developers.openai.com/api/docs/guides/background).
