# Výletní kvíz 2.6.2 — odolnější online příprava

## Soubor

- [Stáhnout podepsané APK](../../../releases/VyletniKviz-2.6.2.apk)
- Verze: 2.6.2, `versionCode 12`
- Balíček: `cz.ctuprotebe.vyletnikviz.quality26`
- SHA-256 APK: `453176e1de93e3c8304712c73a46352869ac1f1c15c303af776fcc3329733f44`
- SHA-256 certifikátu: `830ab6a1051bfe13e35fca889cba5e2d2aa5682ee88db44759bf28ceee0f27aa`
- Podpisy APK Signature Scheme v2 a v3 jsou platné.

Certifikát je stejný jako u 2.6.0 a 2.6.1 a `versionCode` je vyšší. APK se proto instaluje jako aktualizace se zachováním rozehrané hry, kroniky a nastavení. Řada 2.5 používá jiný balíček a zůstává nainstalovaná vedle ní.

## Oprava DNS a pokračování

Snímky z telefonu ukázaly úlohu potvrzenou serverem, po které jediný výpadek DNS během následného GET načítání ukončil přípravu. Verze 2.6.2 používá stejné omezené opakování pro první spojení i pro načítání uložené úlohy. Rozestupy jsou 2, 3, 5, 8, 13 a 21 sekund, celkem přibližně 52 sekund čekání.

Po přijetí OpenAI úlohy se při všech opakováních používá stejné uložené response ID. Test obnovy prokazuje jeden POST, tři simulované DNS výpadky a úspěšné čtvrté GET načtení bez duplicitního placeného požadavku. Nejasný timeout POSTu se úmyslně automaticky neopakuje, protože server mohl požadavek přijmout.

[Build a testy změny 35014549297](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/35014549297) prošly: sestavení, jednotkové testy, live harness bez skutečného API a Android UI testy.

## Test přesně distribuovaného APK

[GitHub Actions run 35015611667](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/35015611667) nainstaloval přesně tento podepsaný soubor na čistý emulátor Android 16 / API 36. Výsledek: `passed=true`, 112 kontrol, žádná chyba aplikace a 0 API volání.

Scénář skutečnými klepnutími ověřil:

- spuštění a obrazovku AI připojení;
- systémový výběr fotografie;
- pět pojmenovaných hráčů;
- celou offline hru o 30 otázkách;
- předání otázky za 0,6 bodu;
- odpovědi, vysvětlení a bodování;
- zpět a vpřed bez dvojího přičtení;
- uložení, ukončení procesu a pokračování;
- výsledky, vítěze, kroniku a dlouhodobou tabulku;
- přeinstalaci stejného APK se zachováním historie.

Předchozí pokus 35014941929 zastavil systémový launcher API-36 emulátoru, který přes aplikaci zobrazil roletu a poté dialog „Quickstep isn't responding“. Testovací ovladač byl upraven tak, aby tyto systémové překryvy zavřel; distribuované APK se kvůli tomu neměnilo.

## Online generování a hranice výsledku

Produkční generátor a transport před touto opravou dokončily skutečný omezený online test 10/10 v [runu 34940846081](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34940846081). Nový DNS scénář je deterministicky simulován v jednotkových testech; další placený run kvůli síťovému retry nebyl potřeba.

Trvale nefunkční DNS, VPN, blokátor nebo síťový filtr aplikace nemůže opravit. Po přibližně minutě zobrazí chybu a zachová hotové části i ID úlohy pro pozdější pokračování. Testy také nejsou zárukou stoprocentní správnosti každé budoucí AI otázky. Historie se mezi telefony automaticky nesynchronizuje a lokální URI fotografie není přenosná záloha obrazového souboru.
