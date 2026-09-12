# Výletní kvíz

Android aplikace pro 2–5 pojmenovaných hráčů u jednoho telefonu. Pevné pořadí, přebírání otázek, body, kronika výletů a fotografie. Připravená hra funguje offline.

Při „Neví“ se náhodně vyřadí chybná možnost, dokud zbývají alespoň dvě. Další hráči mohou otázku převzít za nižší bodovou hodnotu; správná možnost se nikdy automaticky nevyřazuje. Opakované zpracování stejné odpovědi ani výsledků nesmí připsat body nebo historii dvakrát. Import kontroluje strukturu záznamu a stejný identifikátor výletu znovu nezapočítává.

## AI příprava ve verzi 2.5.0

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

APK je vývojové sestavení `quality` s applicationId `cz.ctuprotebe.vyletnikviz.quality` a názvem „Výletní kvíz 2.5“. Instaluje se vedle původní aplikace, protože její podpisový klíč není dostupný. Stará aplikace a její data zůstávají zachované. Do nové se klíč zadá znovu; výlety lze přenést přes Sdílet tento výlet / Importovat sdílený výlet. Přenos dat není automatický. Pro budoucí běžné aktualizace je stále nutné vyřešit stabilní neveřejný podpisový klíč; výchozí debug klíč nového CI runneru jej nenahrazuje. Fotografie uložené jako lokální URI nejsou přenosnou zálohou obrazových souborů.

## Volitelný placený test skutečného API

1. V [novém GitHub Actions secretu](https://github.com/danielpohlcze-dev/vyletni-kviz/settings/secrets/actions/new) vyplňte Name `OPENAI_API_KEY`, vložte klíč do Secret a zvolte Add secret. Klíč nepatří do chatu, souboru v repozitáři ani snímku obrazovky.
2. Otevřete [Živý test AI — 10 otázek](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/workflows/live-quiz.yml), zvolte Run workflow na větvi main. Alternativně jej lze spustit výslovnou změnou `tests/live-trigger.txt`.
3. Výsledkem je artefakt `live-quiz-result`: skutečné otázky se zdroji a souhrn HTTP stavu, doby, tokenů a počtu požadavků. Klíč ani libovolné texty výjimek se nezapisují. Bez secretu se nic placeného neodešle. Běžné sestavení APK placený test nespouští.

Program kompiluje přímo produkční `QuizGeneration` a `OpenAiTransport`. Zkouší i obnovu po jednom simulovaném přerušení pollingu skutečné serverové úlohy. Pro omezení nákladů povoluje nejvýše čtyři pokusy POST (autor + kontrola pro dvě skupiny po pěti), 18 000 výstupních tokenů a navíc osm webových volání na požadavek. Pokud je potřeba více oprav, test skončí neúspěchem a vykáže přijatý počet otázek. Limity nejsou pevným dolarovým rozpočtem; účtování probíhá z vloženého OpenAI kreditu. Nejde o dlouhodobý zátěžový test ani o test mobilní sítě. Výstup je třeba obsahově zkontrolovat, úspěšný běh negarantuje bezchybnost každé budoucí otázky.

Test průběžně ukládá také `resume.json` a `accepted-questions.json` do svého artefaktu. Pro navázání lze přesný checkpoint posledního běhu umístit do `live-test/resume.json`; plán musí odpovídat testovací sadě. Jde výhradně o testovací otázky a metadata bez API klíče. První živý běh 34705609000 ještě tuto archivaci neměl: přijal 7/10 otázek za čtyři požadavky a úspěšně obnovil přerušený polling, potom skončil na testovacím limitu `BudgetExceeded`. Nešlo o chybu kreditu. Sedm otázek z tohoto prvního běhu se do artefaktu neuložilo, proto je nelze z něj obnovit. Další běhy již poskytují checkpoint a konkrétní důvody zamítnutí pro kontrolu.

Dokumentace API: [model](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [webové zdroje](https://developers.openai.com/api/docs/guides/tools-web-search), [úlohy na pozadí](https://developers.openai.com/api/docs/guides/background).
