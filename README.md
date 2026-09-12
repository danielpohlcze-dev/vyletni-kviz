# Výletní kvíz

Android aplikace pro 2–5 pojmenovaných hráčů u jednoho telefonu. Pevné pořadí, přebírání otázek, body, kronika výletů a fotografie. Připravená hra funguje offline.

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

Fotografický test ověřuje odeslání intentu pro Android picker se simulovaným zrušením; nedokládá skutečné pořízení fotky. Některé stávající UI testy používají programatické kliknutí kvůli okraji emulátoru.

APK je vývojové sestavení `quality` s applicationId `cz.ctuprotebe.vyletnikviz.quality` a názvem „Výletní kvíz 2.5“. Instaluje se vedle původní aplikace, protože její podpisový klíč není dostupný. Stará aplikace a její data zůstávají zachované. Do nové se klíč zadá znovu; výlety lze přenést přes Sdílet tento výlet / Importovat sdílený výlet. Přenos dat není automatický. Pro budoucí běžné aktualizace je stále nutné vyřešit stabilní neveřejný podpisový klíč; výchozí debug klíč nového CI runneru jej nenahrazuje. Fotografie uložené jako lokální URI nejsou přenosnou zálohou obrazových souborů.

Dokumentace API: [model](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [webové zdroje](https://developers.openai.com/api/docs/guides/tools-web-search), [úlohy na pozadí](https://developers.openai.com/api/docs/guides/background).
