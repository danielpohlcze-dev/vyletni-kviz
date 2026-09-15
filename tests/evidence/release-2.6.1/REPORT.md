# Výletní kvíz 2.6.1 — finální instalační APK

## Soubor

- [Stáhnout podepsané APK](../../../releases/VyletniKviz-2.6.1.apk)
- Verze: 2.6.1, `versionCode 11`
- Balíček: `cz.ctuprotebe.vyletnikviz.quality26`
- SHA-256 APK: `d585b46fd1f49de4f482427f237fa7144e593a3786eafb63270f76b7879f8034`
- SHA-256 certifikátu: `830ab6a1051bfe13e35fca889cba5e2d2aa5682ee88db44759bf28ceee0f27aa`
- Podpisy APK Signature Scheme v2 a v3 jsou platné.

Certifikát je stejný jako u 2.6.0 a `versionCode` je vyšší, proto lze APK nainstalovat jako aktualizaci 2.6.0 se zachováním jejích dat. Proti starší řadě 2.5 používá jiný balíček a instaluje se vedle ní.

## Test přesně distribuovaného APK

[GitHub Actions run 34990798621](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34990798621) nainstaloval tento konkrétní podepsaný soubor na čistý emulátor Android 16 / API 36. Výsledek: `passed=true`, 112 kontrol, žádná chyba.

Scénář skutečnými klepnutími ověřil:

- spuštění a obrazovku AI připojení;
- systémový výběr fotografie;
- pět pojmenovaných hráčů;
- celou offline hru o 30 otázkách;
- předání otázky dalšímu hráči za 0,6 bodu;
- správnou odpověď, vysvětlení a bodování;
- zpět a vpřed bez dvojího přičtení;
- uložení hry, ukončení procesu a pokračování;
- výsledky, vítěze, kroniku a dlouhodobou tabulku;
- přeinstalaci stejného APK se zachováním historie.

## Online generování

Stejný produkční generátor a transport před vydáním prošly skutečným omezeným online testem 10/10: [run 34940846081](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34940846081). Test použil dva POST požadavky pro novou pětici (autor a kontrolor), povinné webové zdroje a bezpečně navázal po simulovaném přerušení pollingu bez duplicitního POSTu. Podrobný obsahový audit je v [online zprávě](../live-2026-09-15/REPORT.md).

Pro online tvorbu musí uživatel v aplikaci uložit vlastní OpenAI API klíč s dostupným kreditem. Offline hra funguje bez klíče. Klíč se ukládá pomocí Android Keystore, ale tato osobní verze stále volá OpenAI přímo z telefonu; nejde o původně požadovaný oddělený bezpečný server.

## Hranice výsledku

Testy prokazují funkčnost této sestavy a konkrétních scénářů, nikoli absolutní bezchybnost každé budoucí AI otázky. Historie se mezi telefony automaticky nesynchronizuje a lokální URI fotografií není přenosná záloha fotografie.
