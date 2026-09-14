# Audit úplnosti Výletního kvízu 2.6 — 14. 9. 2026

Kontrolovaná distribuovaná APK: `releases/VyletniKviz-2.6.0.apk`, SHA-256 `e5e5e054b202ec892974c99bbcbf219b6404eb6a7abc6b5145036005c2f2b1b5`. Výchozí main: `394f892e989ee80061015755a1fa4164055aec51`. Nové kontrolní commity mění testovací skripty, nikoli tuto aplikaci.

## Zjištěné mezery vůči zadání

| Požadavek | Skutečný stav ve verzi 2.6 | Důsledek |
| --- | --- | --- |
| Zvolená obtížnost a okruhy | AI plán je používá. Offline cesta jen načte a zkrátí pevný asset; nefiltruje podle okruhů ani obtížnosti. | Offline nastavení slibuje více, než sada plní. |
| Bizarní otázka pravidelně pro každého | Interval se počítá z globálního pořadí otázky, ne osobních kol. Silné téma má přednost. | U pěti hráčů / 30 otázek / výchozích intervalů jsou bizarní sloty 5, 10, 20 a 25, všechny pro pátého hráče. |
| Fotky a kompletní přenos výletu bratrovi | Fotka na původním zařízení funguje, sdílení přiloží obrázky a text. Import textu však ponechává původní lokální URI; chybí příjem a připojení obrazových souborů v cílové aplikaci. | Přenos kroniky s funkčními fotografiemi na druhý telefon není dokončený. |
| Předání rozehrané hry / moderátora | Exportuje se dokončený záznam výletu. Není export/import aktuálního herního stavu, sdílená skupina ani synchronizace. | Bratr může hrát a ručně importovat výsledky; automaticky převzít rozehranou hru neumí. |
| Dlouhodobá kronika a tabulka | Uložení a import ponechají nový výlet a nejvýše 99 starších. Žebříček se počítá jen z těchto záznamů. | Při 101. uloženém výletu nejstarší záznam z kroniky vypadne i s příspěvkem do dlouhodobých statistik; chybí upozornění a úplná záloha. |
| AI přes bezpečný server | Osobní APK přímo používá OpenAiTransport a API klíč v telefonu. Konstanta starého serveru se v generování nepoužívá. | Architektura neodpovídá původnímu serverovému zadání. Samotné připojení GitHub secretu nenastaví telefon. |
| Živé dokončení 30 otázek bez pádu | Nová 2.6 má simulované regresní testy, živý historický test dokončil jen 10 otázek před touto opravou. | Živé generování 30 otázek na telefonu potvrzené není. Původní dva dodatečné placené běhy jsou vyčerpané. |

Výpočet bizarních slotů je odvozen přímo z podmínek `specialForIndex`; není vydáván za další živý API test. Nálezy o importu a offline nastavení jsou z kontroly skutečných obslužných metod `offlineStart`, `shareTrip`, `importTrip`, `TripHistory.parse` a `runGeneration`.

## Co dosavadní automatické testy skutečně ověřují

31 unit testů: generování a validace, zachování návrhu, menší dávky, restart checkpointu, síťové chyby a historie. 17 instrumentačních testů na Androidu 15: nastavení hráčů, bodování, přebírání, zpět/vpřed, 40otázková hra, uložení a obnovení, kronika a žebříček, sdílecí dialog/import záznamu, fotka z testovacího MediaStore URI a chybová hláška.

Běžné CI bylo při tomto auditu znovu spuštěno: [běh 34812320251](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34812320251). Prošlo. Stále jde o oddělené simulace a emulatorové scénáře, nikoli o důkaz úplnosti všech požadavků.

## Nový test distribuované APK

Test instaluje přesné podepsané APK a ovládá ho přes skutečné dotyky na Androidu 16/API 36. Nemá API klíč; mobilní data a Wi-Fi jsou vypnuté. Plánovaný scénář zahrnuje pět jmen, třicet offline otázek, přebírání, zpět/vpřed, ukončení procesu a navázání, výsledky a kroniku a opětovnou instalaci se zachováním dat.

První běh [34812320350](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34812320350) APK nainstaloval, ale před první herní kontrolou nezískal UI hierarchii. Uložil snímek plochy emulátoru; tento běh selhal a neověřil herní scénář.

Druhý běh [34812543776](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34812543776) potvrdil spuštění Activity (Status: ok), hlavní nabídku, obrazovku připojení bez klíče, otevření systémového výběru fotografie a úpravu názvu a jmen. Skončil na neúspěšném automatickém vyhledání tlačítka přidání pátého hráče při posouvání formuláře. Crash log je prázdný. Není to důkaz pádu aplikace, ale ani dokončení plánované hry. Pro další běh se posouvací gesto přesunulo mimo editovatelná pole a přidala se historie navigace.

Třetí běh [34812884352](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34812884352) prošel přidáním všech pěti jmen, volbou 30 otázek a otevřením offline potvrzení. Zastavil se na rozdílu v automatickém rozpoznávání tlačítka: Android 16 je vykresluje jako „HRÁT VŠEOBECNÝ KVÍZ“. Navigační záznam dokládá přítomnost tohoto tlačítka. Upraven byl pouze matcher tlačítek v testu, nikoli aplikace nebo očekávané výsledky hry.

Úspěch či neúspěch celého třicetiotázkového scénáře je nutné číst z konečného výsledku, ne z jednotlivých předchozích dílčích kroků. Během auditu se spustily pouze neplacené testy; produkční API klíč nebyl čten ani použit.

## Namátková obsahová kontrola

Tento audit není novým doložením všech 40 offline otázek. U dvou vybraných položek byl porovnán obsah s primárními zdroji:

Struktura pevné banky byla zkontrolována programově: 40 různých zadání, u každého čtyři různé možnosti a platný index správné odpovědi. To samo neověřuje pravdivost faktů.

- Otázka 6: tři hlavní města Jihoafrické republiky. [Vládní přehled](https://www.gov.za/about-sa/south-africa-glance) uvádí Pretorii, Kapské Město a Bloemfontein; upozorňuje také na Ústavní soud v Johannesburgu. Vysvětlení v aplikaci mluví o tradičním sídle justice a tento rozdíl nepřepisuje chybným tvrzením o sídle Ústavního soudu.
- Otázka 29: největší CHKO jsou Beskydy. Potvrzuje [oficiální charakteristika AOPK](https://beskydy.aopk.gov.cz/charakteristika-oblasti).

Z těchto dvou kontrol nelze odvozovat faktickou správnost celé banky ani budoucích AI otázek.
