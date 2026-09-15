# Živý test online generování — 15. 9. 2026

## Výsledek

Omezený test skutečného OpenAI API v GitHub Actions dokončil sadu **10/10** otázek. Navázal z přesného checkpointu s pěti dříve přijatými otázkami a vytvořil pouze sloty 6–10. Použil stejný produkční `QuizGeneration` a `OpenAiTransport` jako Android aplikace, model `gpt-5.6-sol` s reasoning `high` a povinné webové zdroje.

- Run: [34940846081](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34940846081)
- Commit testu: [`8395dc6`](https://github.com/danielpohlcze-dev/vyletni-kviz/commit/8395dc6675f0ed2e02e456ba6b8799c4aff56b94)
- Výsledek: `passed=true`, 10 otázek, 164 sekund
- Požadavky: 2 POST (autor a nezávislá kontrola nové skupiny), 75 GET při bezpečném pollingu
- Spotřeba hlášená API: 116 567 vstupních a 7 154 výstupních tokenů
- Webové volání: 13 celkem; limit zůstává nejvýše 8 na jeden požadavek
- Obnova: test úmyslně přerušil jeden GET, navázal podle uloženého response ID a neposlal duplicitní POST
- API klíč zůstal pouze v GitHub Actions secretu a není ve výstupech

## Kontrola výstupu

Automatická i následná kontrola potvrdila:

- přesně 10 unikátních otázek ve slotech 1–10;
- pevné pořadí Barča, Dominik, Daneček, opakovaně;
- u každé otázky čtyři navzájem různé možnosti a platný index správné odpovědi;
- neprázdné vysvětlení, kontrolní poznámku a alespoň jeden HTTPS zdroj;
- shodu témat včetně českého rapu, slovenské politiky 2020–2024, obtížnější angličtiny a bizarního přírodního jevu;
- žádnou duplicitní otázku ani možnost.

Citované stránky byly znovu otevřeny a tvrzení křížově zkontrolována. Zvláštní pozornost dostaly Prago Union (oficiální biografie: Kato a DJ Skupla, vznik kolem roku 2002), vláda Eduarda Hegera (oficiální web prezidentky: jmenování 1. 4. 2021), `circumspect` (Collins: obezřetný/opatrný) a vlasový led (odborný článek v *Biogeosciences*: nutná aktivita houby, ve všech vzorcích `Exidiopsis effusa`). Kontrola nenašla faktickou nebo logickou chybu v této konkrétní sadě.

## Nalezená drobnost

Soubor `accepted-questions.json` měl vedle skutečných deseti otázek pomocný příznak `complete:false`. Neměnilo to `quiz.json`, úspěch běhu ani aplikaci; šlo o chybu exportu testovacího artefaktu. Oprava odvozuje příznak z počtu přijatých slotů a má regresní test. Další placené generování kvůli tomu není potřeba.

## Omezení závěru

Toto je skutečný online test jedné kontrolované sady o deseti otázkách a potvrzuje průchod API, obnovu pollingu i kvalitativní brány. Není to záruka, že každá budoucí náhodně vygenerovaná otázka bude stoprocentně správná, ani test nestabilní mobilní sítě. Generování třiceti otázek v telefonu opakuje stejný mechanismus po menších skupinách, ale nebylo v tomto běhu provedeno.
