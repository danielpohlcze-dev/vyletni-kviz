# Skutečný online test AI — 14. 9. 2026

Test použil pouze workflow `.github/workflows/live-quiz.yml` a `OPENAI_API_KEY` předaný procesu z GitHub Actions repository secretu. Klíč nebyl čten, zobrazen ani uložen do artefaktu. Model a kvalitativní nastavení zůstaly `gpt-5.6-sol` s reasoning `high`; každý běh měl limit čtyř POSTů a osmi webových volání na požadavek.

## Výsledek

Online generování nyní **nefunguje kvůli vyčerpanému API kreditu**. Diagnostický běh [34846103265](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34846103265) vrátil terminální stav `failed` s bezpečným strojovým kódem `credit_balance_exhausted`. Vytvoření otázek nezačalo: 0 vstupních tokenů, 0 výstupních tokenů a 0 webových hledání. Proběhl jeden POST, dva GETy a záměrně přerušený polling byl obnoven podle uloženého ID bez duplicitního POSTu. Zůstalo pět dříve schválených otázek; pět nových nevzniklo.

Předchozí omezené běhy [34844936211](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34844936211) a [34845162629](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34845162629) skončily shodně po jednom POSTu ještě před tokenovou spotřebou, ale tehdejší diagnostika jejich chybový kód zahodila jako `unknown`. Po opravě diagnostiky prošly unit testy, live-harness testy a sestavení v [běhu 34845549439](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34845549439). Emulatorová větev tohoto běhu měla plošnou chybu ztráty fokusu Espresso u devíti testů; tato změna zasahovala pouze čtení terminálního kódu. Dřívější distribuovaná APK prošla celým 30otázkovým UI scénářem v [běhu 34813188409](https://github.com/danielpohlcze-dev/vyletni-kviz/actions/runs/34813188409).

## Další krok

Na účtu OpenAI je potřeba doplnit API kredit nebo odstranit příslušný billing limit. Tento repozitář neumí zjistit výši zůstatku a nic nedobíjel. Po doplnění stačí navázat z posledního checkpointu; žádné nové placené pokusy se nemají spouštět předtím.
